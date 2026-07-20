package com.sangyoon.aiglow

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Direction in which the ring's blurred halo bleeds relative to the shape edge.
 *
 * Why an enum property on [GlowConfig] instead of a separate modifier or component
 * parameter: the inner glow is the *same* halo — same colors, width, blur — merely
 * clipped to the other side of the outline. Modeling it as a config property keeps
 * the whole style/state machinery ([AiGlowStyle], interactive resolution, animated
 * alpha) working unchanged and adds zero new parameters to the components.
 *
 * (한국어) 링 halo가 도형 가장자리를 기준으로 번지는 방향입니다. 별도 Modifier나
 * 컴포넌트 파라미터가 아니라 GlowConfig 속성으로 둔 이유: 안쪽 글로우는 같은 halo를
 * 반대쪽으로 클리핑한 것뿐이라, 속성 하나면 기존 스타일/상태 체계가 그대로 동작하고
 * 컴포넌트에 새 파라미터를 추가할 필요가 없기 때문입니다.
 */
enum class HaloDirection {
    /**
     * Bleeds outward past the edge — the classic outer glow (default).
     * (한국어) 가장자리 바깥으로 번지는 기본 글로우.
     */
    Outward,

    /**
     * Bleeds inward across the component's surface, like light spilling in from the
     * border. Drawn *above* the content so opaque containers don't hide it.
     * (한국어) 테두리에서 컴포넌트 안쪽으로 흘러드는 글로우. 불투명 컨테이너에 가려지지
     * 않도록 콘텐츠 *위*에 그려집니다.
     */
    Inward,

    /**
     * Bleeds both ways symmetrically. Note: rendered as two complementary clipped
     * passes, which can leave a hairline (~1px) seam along the outline if the crisp
     * ring doesn't cover it — i.e. with `strokeWidth = 0.dp` or translucent ring
     * colors. The default 2dp opaque ring masks it completely.
     * (한국어) 안팎 양방향으로 번집니다. 상보적인 두 클리핑 패스로 합성되므로 링이
     * 가리지 못하는 설정(strokeWidth = 0.dp 또는 반투명 링 색)에서는 외곽선을 따라
     * ~1px의 미세한 seam이 보일 수 있습니다. 기본 2dp 불투명 링은 완전히 가립니다.
     */
    Both,
}

/**
 * Immutable set of visual parameters for a single glow state.
 *
 * Why an `@Immutable` data class: the Compose compiler cannot infer stability for
 * `List` or interface types like [Shape]. `@Immutable` is a developer contract that
 * "no public property ever changes after construction", which lets every composable
 * and modifier reading this config skip recomposition as long as an equal value is
 * passed (see the official Compose Stability guide:
 * developer.android.com/develop/ui/compose/performance/stability).
 *
 * Why `copy()` instead of a Builder: a Builder exposing `var` fields would break the
 * immutability contract above. `copy()` produces a new structurally-equal instance,
 * so changing one parameter preserves skip-optimization for all the others.
 *
 * (한국어) 글로우 한 가지 상태의 시각 파라미터를 담는 불변 설정 객체입니다.
 * Compose 컴파일러는 List/Shape의 안정성을 추론하지 못하므로 `@Immutable` 계약으로
 * "생성 후 불변"을 보장해 recomposition skip 최적화를 가능하게 합니다.
 * var Builder 대신 copy() 기반 커스터마이징을 사용해 이 계약을 유지합니다.
 *
 * @property colors Cyclic perimeter palette used by the ring, or by the surface edge
 *   in [aiGlowBackground]. Treat the list as immutable (mutating it after construction
 *   violates the `@Immutable` contract). If the first and last colors differ, the
 *   first color is appended at draw time to close the loop.
 *   (한국어) 링과 [aiGlowBackground]의 표면 테두리에 쓰는 순환 둘레 팔레트입니다.
 *   불변으로 취급해야 하며, 첫/끝 색이 다르면 그리기 시점에 첫 색을 덧붙여 순환을
 *   닫습니다.
 * @property strokeWidth Thickness of the crisp gradient ring, in [Dp] for density
 *   independence. (한국어) 선명한 링의 두께(Dp).
 * @property blurRadius How far the soft halo bleeds outward. `0.dp` disables the halo.
 *   Rendered with [android.graphics.BlurMaskFilter] on API 28+ (hardware-accelerated
 *   since the Skia HWUI pipeline); below API 28 a layered-stroke approximation is used,
 *   so the halo works on every supported API level and never blurs your content.
 *   (한국어) halo가 바깥으로 번지는 반경. API 28+는 BlurMaskFilter, 미만은 다층 스트로크
 *   근사로 그려 모든 지원 API에서 동작하며 콘텐츠는 절대 흐려지지 않습니다.
 * @property rotationDuration Time in milliseconds for one full 360° rotation of the
 *   gradient. (한국어) 그라디언트가 한 바퀴 도는 시간(ms).
 * @property shape Outline shared by the glow and the host component. This is the
 *   standard Compose [Shape] interface — [aiGlow] accepts custom shapes, while the
 *   background's center-converging fan targets single-contour convex shapes such as
 *   rounded rectangles, capsules, and circles.
 *   (한국어) 글로우와 컴포넌트가 공유하는 표준 Compose Shape입니다. [aiGlow]는 커스텀
 *   shape도 받을 수 있고, 배경의 중심 수렴 fan은 둥근 사각형·캡슐·원처럼 단일
 *   외곽선의 볼록한 shape를 대상으로 합니다.
 * @property haloColors Optional separate palette for the blurred halo ("shadow color").
 *   `null` falls back to [colors]. (한국어) halo(셰도우) 전용 색. null이면 colors 사용.
 * @property haloStrokeWidth Optional thickness of the blurred halo ring. `null` falls
 *   back to `strokeWidth * 2`, which reads as a natural light spread.
 *   (한국어) halo 링 두께. null이면 strokeWidth * 2.
 * @property alpha Overall glow opacity in 0..1. Components animate this value between
 *   interaction states, so alpha changes never rebuild the draw cache.
 *   (한국어) 글로우 전체 불투명도(0..1). 상태 전환 시 이 값만 애니메이션되므로
 *   draw 캐시가 재생성되지 않습니다.
 * @property animated `false` freezes the gradient at angle 0° (used e.g. for disabled
 *   states, and useful for screenshot tests). (한국어) false면 회전을 멈춥니다.
 * @property easing Easing of the rotation. [LinearEasing] gives a constant, seamless
 *   spin; a non-linear easing produces a pulsing rotation.
 *   (한국어) 회전 easing. 기본 LinearEasing은 일정한 속도로 돕니다.
 * @property haloDirection Which side of the edge the ring's halo bleeds toward:
 *   [HaloDirection.Outward] (default), [HaloDirection.Inward] (inner glow, drawn above
 *   the content) or [HaloDirection.Both]. Only affects the ring ([aiGlow]); the
 *   background glow's bloom is always outward. Declared last so 1.x positional
 *   constructor calls keep compiling.
 *   (한국어) 링 halo가 번지는 방향 — Outward(기본)/Inward(안쪽 글로우, 콘텐츠 위에
 *   그려짐)/Both. 링([aiGlow])에만 적용되고 배경 글로우의 bloom은 항상 바깥입니다.
 *   기존 positional 생성자 호출 호환을 위해 마지막에 선언했습니다.
 */
@Immutable
data class GlowConfig(
    val colors: List<Color> = AiGlowDefaults.GeminiColors,
    val strokeWidth: Dp = 2.dp,
    val blurRadius: Dp = 16.dp,
    val rotationDuration: Int = 4_000,
    val shape: Shape = RoundedCornerShape(28.dp),
    val haloColors: List<Color>? = null,
    val haloStrokeWidth: Dp? = null,
    val alpha: Float = 1f,
    val animated: Boolean = true,
    val easing: Easing = LinearEasing,
    val haloDirection: HaloDirection = HaloDirection.Outward,
) {
    init {
        require(colors.isNotEmpty()) {
            "GlowConfig.colors must contain at least one color. (colors에는 최소 1개의 색이 필요합니다)"
        }
        require(rotationDuration > 0) {
            "rotationDuration must be a positive number of milliseconds. (0보다 큰 ms 값이어야 합니다)"
        }
        require(strokeWidth >= 0.dp) { "strokeWidth must not be negative. (음수 불가)" }
        require(blurRadius >= 0.dp) { "blurRadius must not be negative. (음수 불가)" }
        require(haloColors == null || haloColors.isNotEmpty()) {
            "haloColors must be null or non-empty. (null이거나 비어있지 않아야 합니다)"
        }
        require(haloStrokeWidth == null || haloStrokeWidth >= 0.dp) {
            "haloStrokeWidth must not be negative. (음수 불가)"
        }
        require(alpha in 0f..1f) { "alpha must be within 0..1. (0..1 범위여야 합니다)" }
    }
}

/**
 * Halo palette resolution: dedicated halo colors win, otherwise reuse the ring colors.
 * (한국어) halo 색 결정: 전용 색이 있으면 사용, 없으면 링 색을 재사용.
 */
internal val GlowConfig.resolvedHaloColors: List<Color>
    get() = haloColors ?: colors

/**
 * Halo width resolution: `strokeWidth * 2` reads as a natural light spread by default.
 * (한국어) halo 두께 결정: 기본값은 strokeWidth * 2로 자연스러운 확산을 만든다.
 */
internal val GlowConfig.resolvedHaloStrokeWidth: Dp
    get() = haloStrokeWidth ?: (strokeWidth * 2)
