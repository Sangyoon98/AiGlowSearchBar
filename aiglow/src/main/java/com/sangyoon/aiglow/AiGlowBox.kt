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
 * Two independent glow layers: [glowStyle] draws the edge ring and
 * [backgroundGlowStyle] carries the flowing perimeter colors toward one mixed center
 * color across the surface and blooms outward. The fill is painted *over*
 * [backgroundColor] (when both are set) and *under* the content. Both styles are
 * forced onto the same resolved container
 * `shape` before drawing (see [AiGlowStyle.pinnedToShape]), so per-state or
 * ring-vs-background shape differences can never make the fill/ring diverge from the
 * `clip`/`background` outline used here.
 *
 * Modifier order is deliberate: `aiGlow`, `background` and `aiGlowBackground` all sit
 * *outside* `clip`, so the halo/bloom bleed beyond the shape while the content and
 * ripple stay clipped inside it.
 *
 * Why [backgroundGlowStyle] is the last parameter (after [interactionSource], before
 * the trailing [content] lambda) instead of sitting next to [glowStyle]: this keeps
 * every 1.0.0 parameter at its original position, so existing positional call sites
 * keep compiling against 1.1.0+.
 *
 * (한국어) 어떤 콘텐츠든 글로우로 감싸는 범용 컨테이너입니다. 순수 Modifier와 달리
 * shape 클리핑, shape와 일치하는 배경, ripple 포함 클릭 처리까지 배선하며,
 * InteractionSource를 글로우와 공유해 누르면 glowStyle.pressed가 반응합니다.
 * [backgroundGlowStyle]은 흐르는 둘레 색을 표면 안쪽의 하나의 혼합 중심 색으로 모으고
 * 바깥으로도 번지는 배경 글로우로, [backgroundColor] 위·콘텐츠 아래에 칠해집니다.
 * 두 스타일 모두 그리기
 * 전에 동일한 컨테이너 shape으로 강제 고정되어([AiGlowStyle.pinnedToShape]) clip/배경에
 * 쓰는 외곽선과 어긋나지 않습니다. glow/배경/채움을 clip 바깥에 두어 halo·bloom은 밖으로
 * 번지고 콘텐츠·ripple은 안에 갇힙니다.
 *
 * [backgroundGlowStyle]을 [interactionSource] 뒤, [content] 트레일링 람다 앞에 둔
 * 이유: 1.0.0의 모든 파라미터를 원래 위치 그대로 유지해 기존 positional 호출이
 * 1.1.0+에서도 계속 컴파일되게 하기 위함입니다.
 *
 * @param modifier Layout modifier (set the size here). (한국어) 크기 지정용 Modifier.
 * @param glowStyle Per-interaction-state edge-ring glow; `null` draws no ring.
 *   (한국어) 상태별 테두리 글로우. null이면 링 없음.
 * @param enabled Affects both the click handler and the glow state.
 *   (한국어) 클릭과 글로우 상태 모두에 적용.
 * @param onClick Optional click action; `null` renders a non-interactive container.
 *   (한국어) null이면 클릭 없는 컨테이너.
 * @param backgroundColor Fill behind the content, clipped to the glow shape.
 *   [Color.Unspecified] draws no background — an explicitly inward/both halo may then
 *   show through translucent content. (한국어) shape로 클리핑되는 배경색. Unspecified면
 *   배경이 없으므로 inward/both halo가 반투명 콘텐츠를 통해 보일 수 있습니다.
 * @param contentAlignment Alignment of [content]. (한국어) 콘텐츠 정렬.
 * @param interactionSource Pass your own to observe/share interactions; `null` creates
 *   an internal one. (한국어) 직접 관찰하려면 전달, null이면 내부 생성.
 * @param backgroundGlowStyle Per-interaction-state surface glow; `null` (default)
 *   draws no fill. Its resolved shape should be single-contour and convex.
 *   (한국어) 상태별 배경(표면) 글로우. null(기본)이면 채움이 없으며, 적용할 shape는 단일
 *   외곽선의 볼록한 형태여야 합니다.
 * @param content Anything composable. (한국어) 임의 콘텐츠.
 */
@Composable
fun AiGlowBox(
    modifier: Modifier = Modifier,
    glowStyle: AiGlowStyle? = AiGlowDefaults.interactiveStyle(),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = Color.Unspecified,
    contentAlignment: Alignment = Alignment.TopStart,
    interactionSource: MutableInteractionSource? = null,
    backgroundGlowStyle: AiGlowStyle? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shape = (glowStyle ?: backgroundGlowStyle)?.idle?.shape ?: GlowConfig().shape

    // Draw order (outer → inner): ring halo → backgroundColor → gradient fill+bloom →
    // clipped content → crisp ring on top.
    // (한국어) 그리기 순서(바깥→안쪽): 링 halo → 배경색 → 채움+bloom → 클리핑된 콘텐츠 → 링.
    var chain = modifier
    if (glowStyle != null) {
        chain = chain.aiGlow(glowStyle.pinnedToShape(shape), source, enabled)
    }
    if (backgroundColor.isSpecified) {
        chain = chain.background(backgroundColor, shape)
    }
    if (backgroundGlowStyle != null) {
        chain = chain.aiGlowBackground(backgroundGlowStyle.pinnedToShape(shape), source, enabled)
    }
    chain = chain.clip(shape)
    if (onClick != null) {
        chain = chain.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }

    Box(
        modifier = chain,
        contentAlignment = contentAlignment,
        content = content,
    )
}
