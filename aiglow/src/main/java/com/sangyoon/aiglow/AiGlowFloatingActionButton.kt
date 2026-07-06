package com.sangyoon.aiglow

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A Material 3 [FloatingActionButton] wrapped in a rotating AI glow.
 *
 * Why a thin wrapper instead of a re-implementation: elevation, ripple, shape
 * theming and a11y all come from Material's FAB; this component only injects the
 * glow modifier and shares one [MutableInteractionSource] so `glowStyle.pressed`
 * lights up while the button is held.
 *
 * `glowStyle.idle.shape` is used as the FAB shape too, keeping the glow ring and the
 * button surface perfectly concentric. There is no `enabled` parameter because
 * Material 3's FAB deliberately has none (FABs represent always-available primary
 * actions).
 *
 * (한국어) Material 3 FAB을 감싸는 얇은 래퍼입니다. elevation/ripple/테마/접근성은
 * Material 것을 그대로 쓰고, 같은 InteractionSource를 공유해 누르는 동안
 * glowStyle.pressed가 빛납니다. glowStyle.idle.shape가 FAB shape로도 쓰여 글로우와
 * 버튼 면이 정확히 겹칩니다. enabled 파라미터가 없는 것은 M3 FAB 규약을 따른 것입니다.
 *
 * @param onClick Click action. (한국어) 클릭 동작.
 * @param modifier Layout modifier. (한국어) 배치용 Modifier.
 * @param glowStyle Per-interaction-state glow. (한국어) 상태별 글로우 스타일.
 * @param containerColor FAB surface color. (한국어) FAB 표면 색.
 * @param contentColor Content (icon/text) color. (한국어) 내용물 색.
 * @param elevation Material elevation set. (한국어) Material elevation.
 * @param interactionSource Pass your own to observe/share interactions; `null` creates
 *   an internal one. (한국어) 직접 관찰하려면 전달, null이면 내부 생성.
 * @param content Icon or any composable content. (한국어) 아이콘 등 임의 콘텐츠.
 */
@Composable
fun AiGlowFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowStyle: AiGlowStyle = AiGlowDefaults.interactiveStyle(),
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.aiGlow(glowStyle, source, enabled = true),
        shape = glowStyle.idle.shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = source,
        content = content,
    )
}
