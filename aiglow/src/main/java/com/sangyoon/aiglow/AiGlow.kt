package com.sangyoon.aiglow

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.os.Build
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt

/**
 * Draws a rotating AI glow around the content: a soft blurred halo *behind* the
 * content and a crisp sweep-gradient ring *on top* of its edge.
 *
 * Why a @Composable modifier factory instead of `Modifier.composed`: the rotation
 * must live in composition ([rememberInfiniteTransition]), and `composed` re-runs its
 * lambda on every materialization, defeating skipping (the Compose API guidelines
 * discourage it). A composable factory stores the transition in an independent
 * composition slot **per call site**, so any number of glowing components can never
 * interfere with each other — there is no global or companion state by construction.
 *
 * Why the returned Modifier is remembered: without it, every recomposition of the
 * caller (e.g. each keystroke in a search field) would create a new draw lambda,
 * break Modifier equality and throw away the draw cache. `remember(glowConfig, angle)`
 * pins the instance so the node only updates when the config actually changes.
 *
 * (한국어) 콘텐츠 뒤에는 블러 halo를, 가장자리 위에는 선명한 그라디언트 링을 그리는
 * Modifier입니다. composed 대신 @Composable 팩토리를 쓴 이유: 회전 상태가 호출 지점별
 * composition slot에 격리되어 인스턴스 간 간섭이 구조적으로 불가능하고, composed의
 * skip 무력화 비용도 피할 수 있기 때문입니다. 반환 Modifier를 remember로 고정해
 * 검색어 입력 같은 recomposition에서 draw 캐시가 버려지지 않게 합니다.
 *
 * @param glowConfig Visual parameters; customize via `copy()`. (한국어) copy()로 커스터마이징.
 */
@Composable
fun Modifier.aiGlow(glowConfig: GlowConfig): Modifier {
    val angle = rememberGlowAngle(glowConfig.rotationDuration, glowConfig.animated, glowConfig.easing)
    val glow = remember(glowConfig, angle) {
        // Alpha is routed through the provider so the draw cache is keyed on the
        // structural config only. (한국어) alpha는 provider로 전달해 캐시 키에서 분리.
        Modifier.glowDraw(glowConfig.copy(alpha = 1f), { angle.value }, { glowConfig.alpha })
    }
    return this then glow
}

/**
 * Interaction-aware overload: resolves one [GlowConfig] per state from [glowStyle]
 * (pressed > focused > hovered > idle, with a derived dim state when disabled) and
 * smoothly animates opacity between states.
 *
 * Why only alpha is animated across states: alpha is read inside the draw phase via a
 * provider lambda, so animating it costs zero recomposition and never rebuilds the
 * draw cache. Structural changes (colors, thickness, shape) swap at the state
 * boundary instead, where a one-time cache rebuild is the correct behavior.
 *
 * (한국어) InteractionSource의 focus/press/hover를 관찰해 상태별 config를 고르고,
 * 상태 전환 시 불투명도를 부드럽게 애니메이션하는 오버로드입니다. alpha만 draw phase에서
 * 읽어 recomposition 0회로 전환하고, 구조적 변화(색/두께/모양)는 상태 경계에서 1회만
 * 캐시를 재구성합니다.
 *
 * @param glowStyle Per-state glow configs. (한국어) 상태별 글로우 설정.
 * @param interactionSource Source of focus/press/hover events — pass the same instance
 *   to your clickable/text field so the glow reacts to it. `null` creates a private,
 *   inert source. (한국어) 컴포넌트와 같은 인스턴스를 공유해야 글로우가 반응합니다.
 * @param enabled `false` switches to the disabled config. (한국어) false면 disabled 상태.
 */
@Composable
fun Modifier.aiGlow(
    glowStyle: AiGlowStyle,
    interactionSource: InteractionSource? = null,
    enabled: Boolean = true,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()

    val resolved = glowStyle.resolve(enabled = enabled, focused = focused, pressed = pressed, hovered = hovered)
    // Keyed on the alpha-neutral config: alpha-only state changes (the common case for
    // interactiveStyle) reuse the existing draw cache. (한국어) alpha만 다른 상태 전환은
    // 캐시를 재사용하도록 alpha를 중립화한 config를 remember 키로 쓴다.
    val structural = resolved.copy(alpha = 1f)
    val alpha = animateFloatAsState(targetValue = resolved.alpha, label = "AiGlowAlpha")
    val angle = rememberGlowAngle(structural.rotationDuration, structural.animated, structural.easing)

    val glow = remember(structural, angle, alpha) {
        Modifier.glowDraw(structural, { angle.value }, { alpha.value })
    }
    return this then glow
}

/**
 * Creates the 0°→360° infinitely repeating rotation state.
 *
 * Why separated from drawing: splitting "state creation (composition phase)" from
 * "rendering (draw phase)" lets one transition drive multiple layers and keeps each
 * concern independently testable. The transition is disposed with the composition —
 * no leaks, no global state (per the official animation guide's
 * rememberInfiniteTransition lifecycle).
 *
 * (한국어) 상태 생성(composition)과 그리기(draw)를 층으로 분리했습니다. transition은
 * composition을 떠날 때 함께 정리되므로 누수/전역 상태가 없습니다.
 */
@Composable
internal fun rememberGlowAngle(
    rotationDurationMillis: Int,
    animated: Boolean = true,
    easing: Easing = LinearEasing,
): State<Float> {
    if (!animated) {
        // Static gradient: a constant state keeps the draw path identical while
        // skipping the animation clock entirely. (한국어) 정지 상태는 애니메이션 클럭을
        // 아예 구독하지 않는다.
        return remember { mutableStateOf(0f) }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "AiGlowRotation")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDurationMillis, easing = easing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "AiGlowAngle",
    )
}

/**
 * Closes the sweep-gradient color loop so no seam is visible at the 0°/360° boundary.
 * (한국어) 0°/360° 경계에서 이음새가 보이지 않도록 색 순환을 닫는다.
 */
internal fun closeSweepLoop(colors: List<Color>): List<Color> = when {
    colors.size == 1 -> colors + colors
    colors.first() != colors.last() -> colors + colors.first()
    else -> colors
}

/**
 * The pure draw layer. Creates no composition state.
 *
 * Why drawWithCache: [androidx.compose.ui.graphics.Outline] creation (Path work),
 * shader allocation and the halo [android.graphics.Paint] are too expensive to build
 * per frame; the cache scope keeps them alive until size/config change (official
 * Graphics Modifiers guidance).
 *
 * Why angle/alpha arrive as provider lambdas: deferring the State reads into
 * `onDrawWithContent` means a value change invalidates the **draw phase only** —
 * composition and layout never run during the animation (official performance guide
 * "defer reads" + the three-phase model). At 60–120fps this is the core optimization.
 *
 * Why the shader's local matrix rotates instead of the canvas: rotating the canvas
 * would spin the outline (a rounded rectangle would visibly tilt); rotating the
 * shader matrix moves only the gradient colors along the fixed outline.
 *
 * Why BlurMaskFilter instead of `Modifier.blur()`: `Modifier.blur()` applies a
 * RenderEffect to *everything* the node draws — including your content — and is
 * silently ignored below API 31. The mask filter blurs only the halo stroke, works
 * from API 28 in hardware (Skia HWUI), and below API 28 we approximate the halo with
 * layered translucent strokes, so `blurRadius` is honored on every supported API level.
 *
 * (한국어) 순수 draw 레이어입니다. drawWithCache로 Outline/셰이더/Paint를 캐시하고,
 * angle·alpha는 람다로 지연 읽기해 draw phase만 무효화합니다(애니메이션 중 recomposition 0회).
 * 캔버스 회전은 모양까지 돌리므로 셰이더 local matrix만 회전시키고,
 * Modifier.blur()는 콘텐츠 전체를 흐리게 하고 API 31 미만에서 무시되므로
 * BlurMaskFilter(API 28+) + 다층 스트로크 폴백(API 26~27)으로 halo를 그립니다.
 */
internal fun Modifier.glowDraw(
    config: GlowConfig,
    angleProvider: () -> Float,
    alphaProvider: () -> Float,
): Modifier = drawWithCache {
    val ringColors = closeSweepLoop(config.colors)
    val haloColors = closeSweepLoop(config.resolvedHaloColors)
    val outline = config.shape.createOutline(size, layoutDirection, this)
    val center = size.center

    val ringShader = SweepGradientShader(center = center, colors = ringColors)
    val ringBrush = ShaderBrush(ringShader)
    val ringStroke = Stroke(width = config.strokeWidth.toPx())

    val blurPx = config.blurRadius.toPx()
    val haloWidthPx = config.resolvedHaloStrokeWidth.toPx()
    val haloShader = SweepGradientShader(center = center, colors = haloColors)
    val haloBrush = ShaderBrush(haloShader)
    val shaderMatrix = Matrix()

    // API 28+ (Skia HWUI pipeline): real gaussian halo via BlurMaskFilter.
    // API 26–27: layered-stroke approximation, since mask filters are not
    // hardware-accelerated there. (한국어) 28+는 진짜 블러, 26~27은 다층 근사.
    val useNativeBlur = blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val nativeHaloPath: android.graphics.Path? = if (useNativeBlur) {
        Path().apply { addOutline(outline) }.asAndroidPath()
    } else {
        null
    }
    val nativeHaloPaint: android.graphics.Paint? = if (useNativeBlur) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = haloWidthPx
            shader = haloShader
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }
    val fallbackHaloLayers: List<Pair<Stroke, Float>> = if (blurPx > 0f && !useNativeBlur) {
        val layerCount = 6
        List(layerCount) { index ->
            val fraction = (index + 1) / layerCount.toFloat()
            Stroke(width = haloWidthPx + blurPx * 2f * fraction) to ((1f - fraction) * 0.28f + 0.04f)
        }
    } else {
        emptyList()
    }

    onDrawWithContent {
        // State reads happen here, in the draw phase only. (한국어) 상태 읽기는 draw에서만.
        val alpha = alphaProvider().coerceIn(0f, 1f)
        if (alpha > 0f) {
            shaderMatrix.setRotate(angleProvider(), center.x, center.y)
            ringShader.setLocalMatrix(shaderMatrix)
            haloShader.setLocalMatrix(shaderMatrix)

            // 1) Soft halo, behind the content. (한국어) 콘텐츠 뒤 halo.
            if (nativeHaloPaint != null && nativeHaloPath != null) {
                nativeHaloPaint.alpha = (alpha * 255f).roundToInt()
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(nativeHaloPath, nativeHaloPaint) }
            } else {
                fallbackHaloLayers.forEach { (stroke, layerAlpha) ->
                    drawOutline(outline = outline, brush = haloBrush, alpha = alpha * layerAlpha, style = stroke)
                }
            }
        }

        // 2) The host content itself. (한국어) 호스트 콘텐츠.
        drawContent()

        if (alpha > 0f) {
            // 3) Crisp ring on top, so an opaque container never covers its inner half.
            // (한국어) 불투명 컨테이너가 링 안쪽 절반을 가리지 않도록 맨 위에 그린다.
            drawOutline(outline = outline, brush = ringBrush, alpha = alpha, style = ringStroke)
        }
    }
}
