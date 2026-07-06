package com.sangyoon.aiglow

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role

/**
 * A general-purpose glowing container: wrap *any* composable content in a rotating
 * AI glow, optionally clickable.
 *
 * Why this exists alongside `Modifier.aiGlow`: the raw modifier only draws; this
 * component also wires the pieces most callers want — clipping content to the glow
 * shape, an optional shape-matched background, and a ripple-enabled click handler
 * that shares its [MutableInteractionSource] with the glow so `glowStyle.pressed`
 * reacts to touch.
 *
 * Modifier order is deliberate: `aiGlow` sits *outside* `clip`, so the halo and ring
 * bleed beyond the shape while the content and ripple stay clipped inside it.
 *
 * (한국어) 어떤 콘텐츠든 글로우로 감싸는 범용 컨테이너입니다. 순수 Modifier와 달리
 * shape 클리핑, shape와 일치하는 배경, ripple 포함 클릭 처리까지 배선하며,
 * InteractionSource를 글로우와 공유해 누르면 glowStyle.pressed가 반응합니다.
 * aiGlow를 clip 바깥에 두어 halo/링은 밖으로 번지고 콘텐츠·ripple은 안에 갇힙니다.
 *
 * @param modifier Layout modifier (set the size here). (한국어) 크기 지정용 Modifier.
 * @param glowStyle Per-interaction-state glow. (한국어) 상태별 글로우 스타일.
 * @param enabled Affects both the click handler and the glow state.
 *   (한국어) 클릭과 글로우 상태 모두에 적용.
 * @param onClick Optional click action; `null` renders a non-interactive container.
 *   (한국어) null이면 클릭 없는 컨테이너.
 * @param backgroundColor Fill behind the content, clipped to the glow shape.
 *   [Color.Unspecified] draws no background — note the halo's inner half will then
 *   show through translucent content. (한국어) shape로 클리핑되는 배경색.
 *   Unspecified면 배경 없음(반투명 콘텐츠엔 halo 안쪽이 비칩니다).
 * @param contentAlignment Alignment of [content]. (한국어) 콘텐츠 정렬.
 * @param interactionSource Pass your own to observe/share interactions; `null` creates
 *   an internal one. (한국어) 직접 관찰하려면 전달, null이면 내부 생성.
 * @param content Anything composable. (한국어) 임의 콘텐츠.
 */
@Composable
fun AiGlowBox(
    modifier: Modifier = Modifier,
    glowStyle: AiGlowStyle = AiGlowDefaults.interactiveStyle(),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = Color.Unspecified,
    contentAlignment: Alignment = Alignment.TopStart,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shape = glowStyle.idle.shape
    Box(
        modifier = modifier
            .aiGlow(glowStyle, source, enabled)
            .clip(shape)
            .then(
                if (backgroundColor.isSpecified) {
                    Modifier.background(backgroundColor, shape)
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
