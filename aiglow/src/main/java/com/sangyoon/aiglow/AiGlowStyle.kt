package com.sangyoon.aiglow

import androidx.compose.runtime.Immutable

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
