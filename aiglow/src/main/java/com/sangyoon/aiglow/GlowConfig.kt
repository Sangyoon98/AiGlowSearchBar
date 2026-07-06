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
 * @property colors Colors of the rotating sweep gradient ring. Treat the list as
 *   immutable (mutating it after construction violates the `@Immutable` contract).
 *   If the first and last colors differ, the first color is appended at draw time to
 *   hide the sweep seam.
 *   (한국어) 회전 그라디언트 링의 색 목록. 불변으로 취급해야 하며, 첫/끝 색이 다르면
 *   그리기 시점에 첫 색이 덧붙어 이음새를 없앱니다.
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
 *   standard Compose [Shape] interface — pass `RoundedCornerShape(radius)` for any
 *   corner radius from a sharp rectangle (0.dp) to a capsule, or any custom [Shape].
 *   (한국어) 글로우와 컴포넌트가 공유하는 외곽선. 표준 Shape 인터페이스라
 *   RoundedCornerShape(radius)로 사각형~캡슐을 자유롭게 지정할 수 있습니다.
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
