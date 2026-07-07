package com.sangyoon.aiglow

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A Material 3 [FloatingActionButton] wrapped in a rotating AI glow.
 *
 * Why a thin wrapper instead of a re-implementation: elevation, ripple, shape
 * theming and a11y all come from Material's FAB; this component only injects the
 * glow modifiers and shares one [MutableInteractionSource] so `glowStyle.pressed`
 * lights up while the button is held.
 *
 * Two independent glow layers: [glowStyle] draws the edge ring, [backgroundGlowStyle]
 * fills the button surface with a rotating gradient that blooms outward. When a
 * background glow is set *and* [containerColor] is left at its default, the container
 * becomes transparent (so the fill replaces the Material surface color) and
 * [elevation] drops to zero — a hard shadow under a translucent, self-lit surface
 * reads as a rendering glitch rather than depth. [contentColor] keeps tracking
 * whatever [containerColor] actually resolves to (exactly like 1.0.0's
 * `contentColorFor(containerColor)`), so overriding `containerColor` alongside a
 * background glow still gets a contrast-correct default instead of a hardcoded one.
 *
 * The resolved idle shape is used as the FAB shape too, and both styles are forced
 * onto that same shape before drawing (see [AiGlowStyle.pinnedToShape]), keeping the
 * glow and the button surface perfectly concentric. There is no `enabled` parameter
 * because Material 3's FAB deliberately has none (FABs represent always-available
 * primary actions).
 *
 * Why [backgroundGlowStyle] is the last parameter (after [interactionSource], before
 * the trailing [content] lambda) instead of sitting next to [glowStyle]: this keeps
 * every 1.0.0 parameter at its original position, so existing positional call sites
 * keep compiling against 1.1.0+. Because [containerColor]/[contentColor]/[elevation]'s
 * smart defaults need to see whether a background glow is set — and Kotlin forbids a
 * default expression from referencing a parameter declared later — those three default
 * to `null` and the actual values are resolved in the function body instead.
 *
 * (한국어) Material 3 FAB을 감싸는 얇은 래퍼입니다. elevation/ripple/테마/접근성은
 * Material 것을 그대로 쓰고, 같은 InteractionSource를 공유해 누르는 동안
 * glowStyle.pressed가 빛납니다. 배경 글로우가 지정되고 *동시에* containerColor가
 * 기본값 그대로일 때만 컨테이너가 투명해지고(채움이 표면색을 대체) elevation이 0이
 * 됩니다 — 스스로 빛나는 반투명 표면 아래의 그림자는 입체감이 아니라 렌더링 오류처럼
 * 보이기 때문입니다. contentColor는 containerColor가 실제로 무엇으로 정해지든 그것을
 * 계속 추적합니다(1.0.0의 contentColorFor(containerColor)와 동일) — 배경 글로우와
 * 함께 containerColor를 override해도 하드코딩된 색이 아니라 대비가 맞는 기본값을
 * 얻습니다. enabled 파라미터가 없는 것은 M3 FAB 규약을 따른 것입니다.
 *
 * [backgroundGlowStyle]을 [interactionSource] 뒤, [content] 트레일링 람다 앞에 둔
 * 이유: 1.0.0의 모든 파라미터를 원래 위치 그대로 유지해 기존 positional 호출이
 * 1.1.0+에서도 계속 컴파일되게 하기 위함입니다. containerColor/contentColor/elevation의
 * 스마트 기본값이 배경 글로우 지정 여부를 참조해야 하는데, Kotlin은 더 뒤에 선언된
 * 파라미터를 기본값 식에서 참조할 수 없게 하므로 이 셋은 null을 기본값으로 두고 실제
 * 기본값은 함수 본문에서 계산합니다.
 *
 * @param onClick Click action. (한국어) 클릭 동작.
 * @param modifier Layout modifier. (한국어) 배치용 Modifier.
 * @param glowStyle Per-interaction-state edge-ring glow; `null` draws no ring.
 *   (한국어) 상태별 테두리 글로우. null이면 링 없음.
 * @param containerColor FAB surface color. `null` (default) picks the Material default
 *   normally, or transparent when [backgroundGlowStyle] is set.
 *   (한국어) null(기본)이면 배경 글로우 지정 시 투명, 아니면 Material 기본값.
 * @param contentColor Content (icon/text) color. `null` (default) tracks whatever
 *   [containerColor] resolves to, matching Material's own contrast pairing — unless
 *   [backgroundGlowStyle] is set and [containerColor] was left at its own default too,
 *   in which case it defaults to `onSurface` (a sensible color against transparent).
 *   (한국어) null(기본)이면 containerColor가 실제로 무엇이든 그에 맞는 대비색을
 *   추적합니다. 단, backgroundGlowStyle이 지정되고 containerColor도 기본값 그대로면
 *   투명 배경 위에 어울리는 onSurface를 씁니다.
 * @param elevation Material elevation set. `null` (default) picks the Material default
 *   normally, or zero when [backgroundGlowStyle] is set.
 *   (한국어) null(기본)이면 배경 글로우 지정 시 0, 아니면 Material 기본값.
 * @param interactionSource Pass your own to observe/share interactions; `null` creates
 *   an internal one. (한국어) 직접 관찰하려면 전달, null이면 내부 생성.
 * @param backgroundGlowStyle Per-interaction-state surface glow; `null` (default)
 *   draws no fill. (한국어) 상태별 배경(표면) 글로우. null(기본)이면 채움 없음.
 * @param content Icon or any composable content. (한국어) 아이콘 등 임의 콘텐츠.
 */
@Composable
fun AiGlowFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowStyle: AiGlowStyle? = AiGlowDefaults.interactiveStyle(),
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: FloatingActionButtonElevation? = null,
    interactionSource: MutableInteractionSource? = null,
    backgroundGlowStyle: AiGlowStyle? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shape = (glowStyle ?: backgroundGlowStyle)?.idle?.shape ?: GlowConfig().shape

    val resolvedContainerColor = containerColor
        ?: if (backgroundGlowStyle != null) Color.Transparent else FloatingActionButtonDefaults.containerColor
    val resolvedContentColor = contentColor
        ?: if (backgroundGlowStyle != null && containerColor == null) {
            MaterialTheme.colorScheme.onSurface
        } else {
            contentColorFor(resolvedContainerColor)
        }
    val resolvedElevation = elevation
        ?: if (backgroundGlowStyle != null) {
            FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
        } else {
            FloatingActionButtonDefaults.elevation()
        }

    // Ring outer, fill inner — see AiGlowSearchBar for the layering rationale.
    // (한국어) 링 바깥, 채움 안쪽 — 레이어 순서 근거는 AiGlowSearchBar 참고.
    var glowModifier = modifier
    if (glowStyle != null) {
        glowModifier = glowModifier.aiGlow(glowStyle.pinnedToShape(shape), source, enabled = true)
    }
    if (backgroundGlowStyle != null) {
        glowModifier = glowModifier.aiGlowBackground(backgroundGlowStyle.pinnedToShape(shape), source, enabled = true)
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = glowModifier,
        shape = shape,
        containerColor = resolvedContainerColor,
        contentColor = resolvedContentColor,
        elevation = resolvedElevation,
        interactionSource = source,
        content = content,
    )
}
