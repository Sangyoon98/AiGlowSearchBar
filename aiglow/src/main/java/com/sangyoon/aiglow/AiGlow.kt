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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
    val glow = rememberResolvedGlow(glowStyle, interactionSource, enabled)
    val ring = remember(glow.structural, glow.angle, glow.alpha) {
        Modifier.glowDraw(glow.structural, { glow.angle.value }, { glow.alpha.value })
    }
    return this then ring
}

/**
 * Fills the component's own surface with a rotating sweep-gradient glow: the shape is
 * painted with the gradient and, when [GlowConfig.blurRadius] > 0, a blurred copy of
 * the fill blooms outward — the component itself appears to emit light.
 *
 * This is the *surface* counterpart of [aiGlow] (which draws an edge ring). The two
 * are independent modifiers so each can have its own colors, opacity, rotation speed
 * and easing — chain both for a combined effect:
 * `Modifier.aiGlow(ringStyle).aiGlowBackground(fillStyle)` (ring stays on top because
 * the outer modifier wraps the inner one's drawing).
 *
 * Field semantics in fill mode: `colors` = surface gradient, `alpha` = surface
 * opacity, `blurRadius` = outward bloom distance (0.dp = crisp fill, no bloom),
 * `haloColors` = bloom palette override; `strokeWidth`/`haloStrokeWidth` are unused.
 * The fill is drawn *behind* the host's content — an opaque container/background on
 * the host will cover it, so pair it with transparent container colors (the bundled
 * components handle this automatically).
 *
 * (한국어) 컴포넌트 표면 자체를 회전 그라디언트로 채우는 배경 글로우입니다. blurRadius가
 * 0보다 크면 채움의 블러 사본이 바깥으로 번져(bloom) 컴포넌트가 스스로 빛나는 것처럼
 * 보입니다. [aiGlow](테두리 링)와 독립된 Modifier라 색/투명도/회전 속도를 따로 설정할 수
 * 있고, 둘을 체이닝하면 함께 렌더링됩니다. fill 모드에서 strokeWidth/haloStrokeWidth는
 * 사용되지 않으며, haloColors는 bloom 색을 재정의합니다. 채움은 콘텐츠 *뒤*에 그려지므로
 * 호스트의 불투명 배경이 있으면 가려집니다(제공 컴포넌트들은 자동 처리).
 *
 * @param glowConfig Visual parameters; customize via `copy()`. (한국어) copy()로 커스터마이징.
 */
@Composable
fun Modifier.aiGlowBackground(glowConfig: GlowConfig): Modifier {
    val angle = rememberGlowAngle(glowConfig.rotationDuration, glowConfig.animated, glowConfig.easing)
    val fill = remember(glowConfig, angle) {
        Modifier.glowFillDraw(glowConfig.copy(alpha = 1f), { angle.value }, { glowConfig.alpha })
    }
    return this then fill
}

/**
 * Interaction-aware overload of [aiGlowBackground]: same per-state resolution and
 * animated-alpha transitions as the [aiGlow] style overload, applied to the surface
 * fill instead of the edge ring.
 *
 * (한국어) [aiGlowBackground]의 상태 반응 오버로드 — [aiGlow] 스타일 오버로드와 동일한
 * 상태 해석/알파 전환을 표면 채움에 적용합니다.
 */
@Composable
fun Modifier.aiGlowBackground(
    glowStyle: AiGlowStyle,
    interactionSource: InteractionSource? = null,
    enabled: Boolean = true,
): Modifier {
    val glow = rememberResolvedGlow(glowStyle, interactionSource, enabled)
    val fill = remember(glow.structural, glow.angle, glow.alpha) {
        Modifier.glowFillDraw(glow.structural, { glow.angle.value }, { glow.alpha.value })
    }
    return this then fill
}

/**
 * The composition-side state bundle shared by the ring and fill style overloads:
 * the alpha-neutral resolved config plus the angle/alpha States whose reads are
 * deferred to the draw phase.
 *
 * Why extracted: [aiGlow] and [aiGlowBackground] must resolve interaction state
 * identically; one implementation keeps the priority rules and cache-key discipline
 * (alpha out of the remember key) from drifting apart.
 *
 * (한국어) 링/배경 스타일 오버로드가 공유하는 composition 상태 묶음. 상태 해석 규칙과
 * 캐시 키 규율(alpha는 remember 키에서 제외)이 두 구현에서 어긋나지 않도록 추출했습니다.
 */
internal class ResolvedGlow(
    val structural: GlowConfig,
    val angle: State<Float>,
    val alpha: State<Float>,
)

@Composable
internal fun rememberResolvedGlow(
    glowStyle: AiGlowStyle,
    interactionSource: InteractionSource?,
    enabled: Boolean,
): ResolvedGlow {
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
    return ResolvedGlow(structural, angle, alpha)
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
 * The pure draw layer for the edge ring. Creates no composition state.
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
 * Halo direction ([GlowConfig.haloDirection]): the halo stroke is centered on the
 * edge, so clipping selects which half survives. The outward half is clipped to
 * strictly *outside* the outline and drawn behind the content (also making it
 * alpha-correct over translucent containers, mirroring [glowFillDraw]'s bloom clip);
 * the inward half is clipped to *inside* and drawn **above** the content, because an
 * opaque container would otherwise hide it completely.
 *
 * (한국어) 테두리 링의 순수 draw 레이어입니다. drawWithCache로 Outline/셰이더/Paint를
 * 캐시하고, angle·alpha는 람다로 지연 읽기해 draw phase만 무효화합니다(애니메이션 중
 * recomposition 0회). 캔버스 회전은 모양까지 돌리므로 셰이더 local matrix만 회전시키고,
 * Modifier.blur()는 콘텐츠 전체를 흐리게 하고 API 31 미만에서 무시되므로
 * BlurMaskFilter(API 28+) + 다층 스트로크 폴백(API 26~27)으로 halo를 그립니다.
 * halo 방향: 스트로크가 가장자리 중앙에 걸쳐 있으므로 클리핑으로 살릴 절반을 고릅니다 —
 * 바깥 절반은 외곽선 밖으로만 클리핑해 콘텐츠 뒤에(반투명 컨테이너에서도 alpha 정확),
 * 안쪽 절반은 외곽선 안으로 클리핑해 콘텐츠 **위**에 그립니다(불투명 컨테이너가 가리지
 * 않도록).
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
    val outlinePath = Path().apply { addOutline(outline) }

    val ringShader = SweepGradientShader(center = center, colors = ringColors)
    val ringBrush = ShaderBrush(ringShader)
    val ringStroke = Stroke(width = config.strokeWidth.toPx())

    val blurPx = config.blurRadius.toPx()
    val haloWidthPx = config.resolvedHaloStrokeWidth.toPx()
    val haloShader = SweepGradientShader(center = center, colors = haloColors)
    val haloBrush = ShaderBrush(haloShader)
    val shaderMatrix = Matrix()

    val bleedsOutward = config.haloDirection != HaloDirection.Inward
    val bleedsInward = config.haloDirection != HaloDirection.Outward

    // API 28+ (Skia HWUI pipeline): real gaussian halo via BlurMaskFilter.
    // API 26–27: layered-stroke approximation, since mask filters are not
    // hardware-accelerated there. (한국어) 28+는 진짜 블러, 26~27은 다층 근사.
    val useNativeBlur = blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val nativeHaloPath: android.graphics.Path? = if (useNativeBlur) outlinePath.asAndroidPath() else null
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
    val hasHalo = nativeHaloPaint != null || fallbackHaloLayers.isNotEmpty()

    onDrawWithContent {
        // State reads happen here, in the draw phase only. (한국어) 상태 읽기는 draw에서만.
        val alpha = alphaProvider().coerceIn(0f, 1f)
        if (alpha > 0f) {
            shaderMatrix.setRotate(angleProvider(), center.x, center.y)
            ringShader.setLocalMatrix(shaderMatrix)
            haloShader.setLocalMatrix(shaderMatrix)

            // 1) Outward half of the halo, behind the content, clipped strictly
            // outside the outline so translucent containers never double-composite
            // with it. (한국어) 바깥 절반 halo — 콘텐츠 뒤, 외곽선 밖으로만 클리핑.
            if (hasHalo && bleedsOutward) {
                clipPath(outlinePath, clipOp = ClipOp.Difference) {
                    drawHaloPass(outline, haloBrush, nativeHaloPath, nativeHaloPaint, fallbackHaloLayers, alpha)
                }
            }
        }

        // 2) The host content itself. (한국어) 호스트 콘텐츠.
        drawContent()

        if (alpha > 0f) {
            // 3) Inward half of the halo, above the content — an opaque container
            // would completely hide it if drawn behind. (한국어) 안쪽 절반 halo —
            // 뒤에 그리면 불투명 컨테이너에 가려지므로 콘텐츠 위에 그린다.
            if (hasHalo && bleedsInward) {
                clipPath(outlinePath, clipOp = ClipOp.Intersect) {
                    drawHaloPass(outline, haloBrush, nativeHaloPath, nativeHaloPaint, fallbackHaloLayers, alpha)
                }
            }

            // 4) Crisp ring topmost, so an opaque container never covers its inner half.
            // (한국어) 불투명 컨테이너가 링 안쪽 절반을 가리지 않도록 맨 위에 그린다.
            drawOutline(outline = outline, brush = ringBrush, alpha = alpha, style = ringStroke)
        }
    }
}

/**
 * Draws one halo pass (native BlurMaskFilter stroke on API 28+, layered translucent
 * strokes below) with the caller's clip already applied. Extracted so the outward and
 * inward passes cannot drift apart.
 *
 * (한국어) halo 한 패스를 그립니다(28+는 네이티브 블러, 미만은 다층 스트로크). 호출부가
 * 클리핑을 먼저 적용합니다. 바깥/안쪽 패스 구현이 어긋나지 않도록 추출했습니다.
 */
private fun DrawScope.drawHaloPass(
    outline: Outline,
    haloBrush: Brush,
    nativeHaloPath: android.graphics.Path?,
    nativeHaloPaint: android.graphics.Paint?,
    fallbackHaloLayers: List<Pair<Stroke, Float>>,
    alpha: Float,
) {
    if (nativeHaloPaint != null && nativeHaloPath != null) {
        nativeHaloPaint.alpha = (alpha * 255f).roundToInt()
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(nativeHaloPath, nativeHaloPaint) }
    } else {
        fallbackHaloLayers.forEach { (stroke, layerAlpha) ->
            drawOutline(outline = outline, brush = haloBrush, alpha = alpha * layerAlpha, style = stroke)
        }
    }
}

/**
 * The pure draw layer for the surface fill. Same caching/deferral discipline as
 * [glowDraw], but paints the shape's interior instead of its edge.
 *
 * Rendering: (1) when blurRadius > 0, a blurred *filled* copy of the shape is drawn,
 * clipped to strictly *outside* the outline; (2) the crisp gradient fill covers the
 * interior. Everything is drawn with `onDrawBehind` because a surface belongs
 * strictly under the content.
 *
 * Why the bloom pass is clipped to the outline's exterior rather than relying on the
 * crisp fill to "cover" its interior half: both passes use standard SrcOver alpha
 * blending. At `alpha == 1` the crisp fill fully replaces whatever the bloom painted
 * underneath, but at `alpha < 1` (the common case — [AiGlowDefaults.interactiveStyle]'s
 * idle state alone is already below 1) the deep interior — beyond `blurRadius` from
 * the edge, where the blur mask has no effect — would get painted *twice* at the same
 * alpha, compositing to `1 - (1 - alpha)^2`: visibly more opaque than the edges, where
 * the mask fades toward zero. Clipping the bloom to the exterior means it can never
 * overlap the region the crisp fill paints, so the result is alpha-correct regardless
 * of `alpha`, `blurRadius`, or shape size.
 *
 * (한국어) 표면 채움의 순수 draw 레이어. [glowDraw]와 동일한 캐시/지연 읽기 규율을
 * 따르되 가장자리 대신 내부를 칠합니다. bloom 패스를 외곽선 바깥쪽으로만 클리핑하는
 * 이유: 두 패스 모두 기본 SrcOver 블렌딩을 쓰므로, alpha==1이면 크리스프 채움이 아래
 * bloom을 완전히 대체하지만 alpha<1(흔한 기본값 — interactiveStyle의 idle조차 이미 1
 * 미만)에서는 blurRadius보다 안쪽 깊은 영역이 같은 alpha로 두 번 칠해져
 * 1-(1-alpha)^2로 합성되어 가장자리보다 중심부가 눈에 띄게 진해지는 오파시티 밴딩이
 * 생깁니다. bloom을 외곽선 바깥으로 클리핑하면 크리스프 채움 영역과 절대 겹치지 않아
 * alpha·blurRadius·shape 크기와 무관하게 항상 정확합니다.
 */
internal fun Modifier.glowFillDraw(
    config: GlowConfig,
    angleProvider: () -> Float,
    alphaProvider: () -> Float,
): Modifier = drawWithCache {
    val fillColors = closeSweepLoop(config.colors)
    val bloomColors = closeSweepLoop(config.resolvedHaloColors)
    val outline = config.shape.createOutline(size, layoutDirection, this)
    val center = size.center
    val outlinePath = Path().apply { addOutline(outline) }

    val fillShader = SweepGradientShader(center = center, colors = fillColors)
    val fillBrush = ShaderBrush(fillShader)

    val blurPx = config.blurRadius.toPx()
    val bloomShader = SweepGradientShader(center = center, colors = bloomColors)
    val bloomBrush = ShaderBrush(bloomShader)
    val shaderMatrix = Matrix()

    val useNativeBlur = blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val nativeBloomPath: android.graphics.Path? = if (useNativeBlur) outlinePath.asAndroidPath() else null
    val nativeBloomPaint: android.graphics.Paint? = if (useNativeBlur) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
            shader = bloomShader
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }
    // API 26–27 bloom approximation: translucent strokes expanding outward from the
    // shape edge. (한국어) 26~27 bloom 근사: 가장자리에서 바깥으로 퍼지는 반투명 스트로크.
    val fallbackBloomLayers: List<Pair<Stroke, Float>> = if (blurPx > 0f && !useNativeBlur) {
        val layerCount = 6
        List(layerCount) { index ->
            val fraction = (index + 1) / layerCount.toFloat()
            Stroke(width = blurPx * 2f * fraction) to ((1f - fraction) * 0.28f + 0.04f)
        }
    } else {
        emptyList()
    }

    onDrawBehind {
        val alpha = alphaProvider().coerceIn(0f, 1f)
        if (alpha <= 0f) return@onDrawBehind

        shaderMatrix.setRotate(angleProvider(), center.x, center.y)
        fillShader.setLocalMatrix(shaderMatrix)
        bloomShader.setLocalMatrix(shaderMatrix)

        // 1) Outward bloom, clipped to strictly outside the outline so it never
        // double-composites with the crisp fill below. (한국어) 외곽선 바깥으로만
        // 클리핑된 bloom — 아래 크리스프 채움과 절대 겹치지 않는다.
        clipPath(outlinePath, clipOp = ClipOp.Difference) {
            if (nativeBloomPaint != null && nativeBloomPath != null) {
                nativeBloomPaint.alpha = (alpha * 255f).roundToInt()
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(nativeBloomPath, nativeBloomPaint) }
            } else {
                fallbackBloomLayers.forEach { (stroke, layerAlpha) ->
                    drawOutline(outline = outline, brush = bloomBrush, alpha = alpha * layerAlpha, style = stroke)
                }
            }
        }

        // 2) Crisp gradient surface. (한국어) 선명한 그라디언트 표면.
        drawOutline(outline = outline, brush = fillBrush, alpha = alpha, style = Fill)
    }
}
