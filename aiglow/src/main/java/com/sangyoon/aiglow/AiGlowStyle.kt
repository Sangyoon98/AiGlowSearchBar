package com.sangyoon.aiglow

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape

/**
 * Interaction-state-aware glow styling: one [GlowConfig] per interaction state.
 *
 * Why per-state configs instead of boolean flags: mirroring Material's
 * `ButtonColors`/`TextFieldColors` pattern keeps every state fully customizable
 * (different colors, thickness, speed — anything) while `null` states simply fall
 * back to [idle], so the simple case stays simple.
 *
 * State priority during resolution: disabled > pressed > focused > hovered > idle.
 * When [disabled] is `null`, a dimmed, non-rotating version of [idle] is derived
 * automatically so disabled components are visually quiet by default.
 *
 * (한국어) 인터랙션 상태별 글로우 스타일. Material의 ButtonColors 패턴처럼 상태마다
 * GlowConfig를 통째로 지정할 수 있고, null인 상태는 idle로 폴백합니다.
 * 우선순위: disabled > pressed > focused > hovered > idle.
 * disabled가 null이면 흐리고 회전이 멈춘 idle이 자동 파생됩니다.
 *
 * @property idle Glow shown when there is no interaction. (한국어) 무상호작용 상태.
 * @property focused Glow while focused (e.g. text field focus). `null` = [idle].
 *   (한국어) 포커스 상태. null이면 idle.
 * @property pressed Glow while pressed/clicked. `null` falls back to [focused], then
 *   [idle]. (한국어) 눌림 상태. null이면 focused → idle 순 폴백.
 * @property hovered Glow while hovered (mouse/stylus). `null` = [idle].
 *   (한국어) 호버 상태. null이면 idle.
 * @property disabled Glow when the component is disabled. `null` derives a dimmed,
 *   static [idle]. (한국어) 비활성 상태. null이면 감쇠된 정적 idle 파생.
 */
@Immutable
data class AiGlowStyle(
    val idle: GlowConfig = GlowConfig(),
    val focused: GlowConfig? = null,
    val pressed: GlowConfig? = null,
    val hovered: GlowConfig? = null,
    val disabled: GlowConfig? = null,
)

/**
 * Picks the effective [GlowConfig] for the current interaction state.
 *
 * Why a pure function: resolution has no side effects and no composition state, so it
 * is trivially unit-testable and can be called from any component.
 *
 * (한국어) 현재 인터랙션 상태에 맞는 GlowConfig를 고르는 순수 함수.
 * 부수효과가 없어 단위 테스트가 쉽고 어느 컴포넌트에서든 재사용할 수 있습니다.
 */
internal fun AiGlowStyle.resolve(
    enabled: Boolean,
    focused: Boolean,
    pressed: Boolean,
    hovered: Boolean,
): GlowConfig = when {
    !enabled -> disabled ?: idle.copy(
        alpha = idle.alpha * AiGlowDefaults.DisabledAlpha,
        animated = false,
    )
    pressed -> this.pressed ?: this.focused ?: idle
    focused -> this.focused ?: idle
    hovered -> this.hovered ?: idle
    else -> idle
}

/**
 * Returns a copy of this style with every per-state [GlowConfig] pinned to [shape].
 *
 * Why this exists: a host component resolves a single canonical `shape` for its own
 * container/clip (typically `idle.shape` of whichever style is non-null), but
 * `aiGlow`/`aiGlowBackground` independently resolve interaction state and draw against
 * *that resolved config's own* `.shape` — which can differ per state, or between the
 * ring style and the background style, since `GlowConfig.shape` is a free field on
 * every state. If it ever diverges from the container's canonical shape, the ring or
 * fill is drawn against a different outline than the container's clip/background,
 * silently breaking the "glow and container outline always match" guarantee. Pinning
 * here forces a single source of truth without requiring callers to repeat the shape
 * on every state of every style by hand.
 *
 * (한국어) 모든 상태별 GlowConfig의 shape을 [shape]로 고정한 복사본을 반환합니다.
 * 컴포넌트는 컨테이너 clip/배경용으로 캐노니컬 shape 하나를 정하지만, aiGlow/
 * aiGlowBackground는 각 config 자신의 shape로 독립 렌더링합니다 — 상태별로, 또는
 * 링 스타일과 배경 스타일 사이에서 shape이 달라지면 링·채움이 컨테이너의 clip/배경과
 * 다른 윤곽으로 그려져 "글로우와 컨테이너 외곽선이 항상 일치한다"는 보장이 조용히
 * 깨집니다. 이를 막기 위해 단일 shape으로 강제 통일합니다.
 */
internal fun AiGlowStyle.pinnedToShape(shape: Shape): AiGlowStyle = AiGlowStyle(
    idle = idle.copy(shape = shape),
    focused = focused?.copy(shape = shape),
    pressed = pressed?.copy(shape = shape),
    hovered = hovered?.copy(shape = shape),
    disabled = disabled?.copy(shape = shape),
)
