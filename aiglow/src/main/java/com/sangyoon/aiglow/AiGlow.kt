package com.sangyoon.aiglow

import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt

/**
 * Draws a flowing AI glow around the content: a soft blurred halo *behind* the
 * content and a crisp perimeter-gradient ring *on top* of its edge.
 *
 * Why a @Composable modifier factory instead of `Modifier.composed`: the flow phase
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
 * Modifier입니다. composed 대신 @Composable 팩토리를 쓴 이유: 흐름 상태가 호출 지점별
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
 * Fills the component's surface from its perimeter inward. Colors flow around the
 * fixed outline using the same distance mapping as [aiGlow], then blend toward one
 * phase-invariant palette mixture at the center. No angular color seam can therefore
 * converge on the center point. When [GlowConfig.blurRadius] > 0, the perimeter glow
 * also blooms outward past the edge. The surface fan is designed for single-contour
 * convex shapes such as rounded rectangles, capsules, and circles.
 *
 * This is the *surface* counterpart of [aiGlow] (which draws an edge ring). The two
 * are independent modifiers so each can have its own colors, opacity, rotation speed
 * and easing — chain both for a combined effect:
 * `Modifier.aiGlow(ringStyle).aiGlowBackground(fillStyle)` (ring stays on top because
 * the outer modifier wraps the inner one's drawing).
 *
 * Field semantics in fill mode: `colors` = perimeter-origin surface palette,
 * `alpha` = surface opacity, `blurRadius` = outward bloom distance (0.dp = no bloom),
 * `haloColors` = bloom palette override (falling back to `colors`);
 * `strokeWidth`/`haloStrokeWidth`/`haloDirection` are unused.
 * The fill is drawn *behind* the host's content — an opaque container/background on
 * the host will cover it, so pair it with transparent container colors (the bundled
 * components handle this automatically).
 *
 * (한국어) 테두리를 따라 흐르는 색을 컴포넌트 안쪽으로 확장하는 배경 글로우입니다.
 * 색은 중심에 가까워질수록 phase와 무관한 하나의 팔레트 혼합색으로 모이므로 중심점에
 * 각도별 색 경계가 생기지 않습니다. blurRadius가 0보다 크면 haloColors(없으면 colors)가
 * 같은 둘레 phase를 따라 가장자리 밖으로도 번집니다. surface fan은 둥근 사각형·캡슐·원
 * 같은 단일 외곽선의 볼록한 shape를 대상으로 합니다. [aiGlow](테두리 링)와 독립된
 * Modifier라 색/투명도/회전 속도를 따로 설정할 수 있고, 둘을 체이닝하면 함께
 * 렌더링됩니다. fill 모드에서 strokeWidth/haloStrokeWidth/haloDirection은 사용되지
 * 않습니다. 채움은 콘텐츠 *뒤*에 그려지므로 호스트의 불투명 배경이 있으면
 * 가려집니다(제공 컴포넌트들은 자동 처리).
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
 * Creates one infinitely repeating flow cycle, represented as legacy 0°→360° values.
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
 * The pure draw layer for the edge ring. Creates no composition state.
 *
 * Why drawWithCache: [androidx.compose.ui.graphics.Outline] creation (Path work),
 * mesh allocation and the halo [android.graphics.Paint] are too expensive to build
 * per frame; the cache scope keeps them alive until size/config change (official
 * Graphics Modifiers guidance).
 *
 * Why angle/alpha arrive as provider lambdas: deferring the State reads into
 * `onDrawWithContent` means a value change invalidates the **draw phase only** —
 * composition and layout never run during the animation (official performance guide
 * "defer reads" + the three-phase model). At 60–120fps this is the core optimization.
 *
 * Why a perimeter color carrier plus a continuous path mask: a center-based sweep
 * maps angle to color, so one degree covers different physical distances on the long
 * and short sides of a rectangle. A cached bitmap mesh maps palette phase to
 * accumulated outline length, while the original path supplies one anti-aliased
 * stroke/blur mask. The phase therefore advances uniformly around the perimeter
 * without segment gaps, corner overdraw, or rotating the canvas/outline.
 *
 * Why BlurMaskFilter instead of `Modifier.blur()`: `Modifier.blur()` applies a
 * RenderEffect to *everything* the node draws — including your content — and is
 * silently ignored below API 31. The mask filter blurs only the halo stroke, works
 * from API 28 in hardware (Skia HWUI), and below API 28 we approximate the halo with
 * layered translucent strokes, so `blurRadius` is honored on every supported API level.
 *
 * Halo direction ([GlowConfig.haloDirection]): outward and inward use separate color
 * carriers and complementary clips. Convex outward contours use a one-sided carrier,
 * while convex inward contours use a center-converging fan. Concave and multi-contour
 * shapes fall back to symmetric contour ribbons so the path fill rule can select the
 * correct side. The outward half stays behind the content; the inward half is drawn
 * **above** it because an opaque container would otherwise hide the inner glow.
 *
 * (한국어) 테두리 링의 순수 draw 레이어입니다. drawWithCache로 Outline/mesh/Paint를
 * 캐시하고, angle·alpha는 람다로 지연 읽기해 draw phase만 무효화합니다(애니메이션 중
 * recomposition 0회). 중심각 sweep은 직사각형의 긴 변/짧은 변에서 실제 이동 거리가
 * 달라지므로, 캐시한 bitmap mesh에 누적 둘레 길이 기준 색상 phase를 매핑하고 원본 path를
 * 하나의 연속 AA/blur mask로 사용합니다. 선분 틈이나 모서리 중첩 없이 테두리 어디서나
 * 같은 둘레 비율로 흐르며 캔버스와 외곽선은 회전시키지 않습니다.
 * Modifier.blur()는 콘텐츠 전체를 흐리게 하고 API 31 미만에서 무시되므로
 * BlurMaskFilter(API 28+) + 다층 스트로크 폴백(API 26~27)으로 halo를 그립니다.
 * halo의 바깥쪽과 안쪽은 별도 색상 carrier와 상보적인 clip을 사용합니다. 볼록한 바깥쪽
 * contour에는 단방향 carrier를, 볼록한 안쪽 contour에는 중심으로 모이는 fan을 적용합니다.
 * 오목하거나 contour가 여러 개인 커스텀 shape는 path fill rule이 올바른 면을 고를 수 있도록
 * 대칭 contour ribbon으로 폴백합니다. 바깥쪽은 콘텐츠 뒤에 두고, 안쪽은 불투명 컨테이너에
 * 가려지지 않도록 콘텐츠 **위**에 그립니다.
 */
internal fun Modifier.glowDraw(
    config: GlowConfig,
    angleProvider: () -> Float,
    alphaProvider: () -> Float,
): Modifier = drawWithCache {
    val outline = config.shape.createOutline(size, layoutDirection, this)
    val outlinePath = Path().apply { addOutline(outline) }
    val nativeOutlinePath = outlinePath.asAndroidPath()
    val ringWidthPx = config.strokeWidth.toPx()
    val ringMaskPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = ringWidthPx
        strokeCap = android.graphics.Paint.Cap.BUTT
        strokeJoin = android.graphics.Paint.Join.MITER
    }
    val ringGradient = PerimeterGradient(
        nativeOutlinePath,
        config.colors,
        carrierWidthPx = maxOf(ringWidthPx * 4f, 8f),
    )
    val ringLayer = PerimeterMaskLayer(
        gradient = ringGradient,
        path = nativeOutlinePath,
        maskPaints = listOf(ringMaskPaint),
        width = size.width,
        height = size.height,
        outsetPx = maxOf(ringWidthPx * 2f, 4f),
    )

    val blurPx = config.blurRadius.toPx()
    val haloWidthPx = config.resolvedHaloStrokeWidth.toPx()

    val bleedsOutward = config.haloDirection != HaloDirection.Inward
    val bleedsInward = config.haloDirection != HaloDirection.Outward

    // API 28+ (Skia HWUI pipeline): real gaussian halo via BlurMaskFilter.
    // API 26–27: layered-stroke approximation, since mask filters are not
    // hardware-accelerated there. (한국어) 28+는 진짜 블러, 26~27은 다층 근사.
    val useNativeBlur = blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val haloMaskPaints: List<android.graphics.Paint> = if (useNativeBlur) {
        listOf(android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = haloWidthPx
            strokeCap = android.graphics.Paint.Cap.BUTT
            strokeJoin = android.graphics.Paint.Join.ROUND
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        })
    } else if (blurPx > 0f) {
        val layerCount = 6
        List(layerCount) { index ->
            val fraction = (index + 1) / layerCount.toFloat()
            android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                alpha = (((1f - fraction) * 0.28f + 0.04f) * 255f).roundToInt()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = haloWidthPx + blurPx * 2f * fraction
                strokeCap = android.graphics.Paint.Cap.BUTT
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
        }
    } else {
        emptyList()
    }
    val maxHaloWidthPx = haloWidthPx + if (useNativeBlur) 0f else blurPx * 2f
    val haloOutsetPx = maxHaloWidthPx / 2f +
        if (useNativeBlur) nativeBlurMaskOutsetPx(blurPx) else 0f
    val paddedHaloOutsetPx = haloOutsetPx + 4f
    val outwardHaloGradient = if (haloMaskPaints.isNotEmpty() && bleedsOutward) {
        PerimeterGradient.outward(
            nativeOutlinePath,
            config.resolvedHaloColors,
            carrierOutsetPx = paddedHaloOutsetPx,
        )
    } else {
        null
    }
    val inwardHaloGradient = if (haloMaskPaints.isNotEmpty() && bleedsInward) {
        PerimeterGradient.inward(
            nativeOutlinePath,
            config.resolvedHaloColors,
            carrierInsetPx = paddedHaloOutsetPx,
        )
    } else {
        null
    }
    val outwardHaloLayer = outwardHaloGradient?.let { gradient ->
        PerimeterMaskLayer(
            gradient = gradient,
            path = nativeOutlinePath,
            maskPaints = haloMaskPaints,
            width = size.width,
            height = size.height,
            outsetPx = paddedHaloOutsetPx,
        )
    }
    val inwardHaloLayer = inwardHaloGradient?.let { gradient ->
        PerimeterMaskLayer(
            gradient = gradient,
            path = nativeOutlinePath,
            maskPaints = haloMaskPaints,
            width = size.width,
            height = size.height,
            outsetPx = paddedHaloOutsetPx,
        )
    }

    onDrawWithContent {
        // State reads happen here, in the draw phase only. (한국어) 상태 읽기는 draw에서만.
        val alpha = alphaProvider().coerceIn(0f, 1f)
        if (alpha > 0f) {
            val angle = angleProvider()
            ringGradient.applyPhase(angle)
            outwardHaloGradient?.applyPhase(angle)
            inwardHaloGradient?.applyPhase(angle)

            // 1) Outward halo, behind the content. The exterior clip selects only the
            // visible side of either the optimized one-sided or fallback ribbon.
            // (한국어) 바깥 halo. exterior clip으로 최적화된 단방향 carrier나 폴백
            // ribbon에서 실제로 보여야 할 바깥쪽만 선택합니다.
            if (outwardHaloLayer != null) {
                clipPath(outlinePath, clipOp = ClipOp.Difference) {
                    drawPerimeterLayer(outwardHaloLayer, alpha)
                }
            }
        }

        // 2) The host content itself. (한국어) 호스트 콘텐츠.
        drawContent()

        if (alpha > 0f) {
            // 3) Inward half of the halo, above the content — an opaque container
            // would completely hide it if drawn behind. (한국어) 안쪽 절반 halo —
            // 뒤에 그리면 불투명 컨테이너에 가려지므로 콘텐츠 위에 그린다.
            if (inwardHaloLayer != null) {
                clipPath(outlinePath, clipOp = ClipOp.Intersect) {
                    drawPerimeterLayer(inwardHaloLayer, alpha)
                }
            }

            // 4) Crisp ring topmost, so an opaque container never covers its inner half.
            // (한국어) 불투명 컨테이너가 링 안쪽 절반을 가리지 않도록 맨 위에 그린다.
            drawPerimeterLayer(ringLayer, alpha)
        }
    }
}

/**
 * Draws one cached perimeter color layer through its continuous outline mask.
 * Extracted so crisp ring and clipped halo passes share the exact same compositing
 * path and cannot drift apart.
 *
 * (한국어) 캐시된 둘레 색상 레이어를 연속 외곽선 mask로 합성합니다. 선명한 링과 클리핑된
 * halo 패스가 동일한 합성 경로를 공유하도록 추출했습니다.
 */
private fun DrawScope.drawPerimeterLayer(layer: PerimeterMaskLayer, alpha: Float) {
    drawIntoCanvas { canvas -> layer.draw(canvas.nativeCanvas, alpha) }
}

/**
 * The pure draw layer for a perimeter-origin surface fill.
 *
 * Rendering: (1) when `blurRadius > 0`, a blurred path mask carries the normalized
 * perimeter colors strictly outside the outline; (2) a cached triangle-fan mesh puts
 * the same colors on the boundary and one phase-invariant mixed palette color on
 * every center vertex. GPU interpolation makes the colors originate at the edge and
 * merge continuously at the center. The fan targets single-contour convex shapes.
 *
 * Why the bloom is clipped outside: at `alpha < 1`, overlapping bloom and surface
 * passes would composite to `1 - (1 - alpha)^2` and make the interior too opaque.
 * Keeping them disjoint applies global alpha exactly once to either region.
 *
 * (한국어) 테두리 기점 surface fill의 순수 draw 레이어입니다. `blurRadius > 0`이면
 * 정규화된 둘레 색을 블러 path mask로 외곽선 밖에만 그리고, 내부 triangle-fan mesh는
 * 경계에 같은 둘레 색을, 모든 중심 꼭짓점에 phase 불변 혼합색을 둡니다. GPU 보간으로
 * 색이 가장자리에서 시작해 중심에서 연속적으로 합쳐집니다. fan은 단일 외곽선의 볼록한
 * shape를 대상으로 합니다. bloom과 surface를 분리하는 이유는 `alpha < 1`일 때 두 패스가
 * 겹치면 `1-(1-alpha)^2`만큼 더 진해지기 때문입니다.
 */
internal fun Modifier.glowFillDraw(
    config: GlowConfig,
    angleProvider: () -> Float,
    alphaProvider: () -> Float,
): Modifier = drawWithCache {
    val outline = config.shape.createOutline(size, layoutDirection, this)
    val outlinePath = Path().apply { addOutline(outline) }
    val nativeOutlinePath = outlinePath.asAndroidPath()

    val fillGradient = PerimeterGradient.surface(nativeOutlinePath, config.colors)
    val fillMaskPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    val fillLayer = PerimeterMaskLayer(
        gradient = fillGradient,
        path = nativeOutlinePath,
        maskPaints = listOf(fillMaskPaint),
        width = size.width,
        height = size.height,
        outsetPx = 2f,
    )

    val blurPx = config.blurRadius.toPx()
    val useNativeBlur = blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val bloomMaskPaints: List<android.graphics.Paint> = if (useNativeBlur) {
        listOf(android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        })
    } else if (blurPx > 0f) {
        val layerCount = 6
        List(layerCount) { index ->
            val fraction = (index + 1) / layerCount.toFloat()
            android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                alpha = (((1f - fraction) * 0.28f + 0.04f) * 255f).roundToInt()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = blurPx * 2f * fraction
                strokeCap = android.graphics.Paint.Cap.BUTT
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
        }
    } else {
        emptyList()
    }
    val bloomOutsetPx = when {
        useNativeBlur -> nativeBlurMaskOutsetPx(blurPx)
        blurPx > 0f -> blurPx
        else -> 0f
    } + 4f
    val bloomGradient = if (bloomMaskPaints.isNotEmpty()) {
        PerimeterGradient.outward(
            nativeOutlinePath,
            config.resolvedHaloColors,
            carrierOutsetPx = bloomOutsetPx,
        )
    } else {
        null
    }
    val bloomLayer = bloomGradient?.let { gradient ->
        PerimeterMaskLayer(
            gradient = gradient,
            path = nativeOutlinePath,
            maskPaints = bloomMaskPaints,
            width = size.width,
            height = size.height,
            outsetPx = bloomOutsetPx,
        )
    }

    onDrawBehind {
        val alpha = alphaProvider().coerceIn(0f, 1f)
        if (alpha <= 0f) return@onDrawBehind

        val angle = angleProvider()
        fillGradient.applyPhase(angle)
        bloomGradient?.applyPhase(angle)

        if (bloomLayer != null) {
            clipPath(outlinePath, clipOp = ClipOp.Difference) {
                drawPerimeterLayer(bloomLayer, alpha)
            }
        }
        drawPerimeterLayer(fillLayer, alpha)
    }
}
