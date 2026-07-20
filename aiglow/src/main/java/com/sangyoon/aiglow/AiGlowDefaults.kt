package com.sangyoon.aiglow

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Default values and ready-made palettes for AiGlow components, following the
 * Material `*Defaults` object convention (e.g. `ButtonDefaults`).
 *
 * Why an `object` is acceptable here: the library rule forbids *mutable* global
 * state. Everything in this object is a deeply immutable `val`/`const` or a pure
 * function, so there is no shared state that could couple component instances.
 *
 * (한국어) Material의 *Defaults 관례를 따르는 기본값/팔레트 모음입니다.
 * 금지된 것은 "전역 mutable 상태"이며, 이 object는 전부 불변 val/const와 순수 함수라
 * 인스턴스 간 상태 공유가 발생하지 않습니다.
 */
object AiGlowDefaults {

    /** Gemini-like blue→purple→rose palette. (한국어) Gemini풍 기본 팔레트. */
    val GeminiColors: List<Color> = listOf(
        Color(0xFF4285F4),
        Color(0xFF9B72CB),
        Color(0xFFD96570),
        Color(0xFF9B72CB),
    )

    /** Cyan→blue→magenta aurora palette. (한국어) 오로라 팔레트. */
    val AuroraColors: List<Color> = listOf(
        Color(0xFF00E5FF),
        Color(0xFF2979FF),
        Color(0xFFD500F9),
    )

    /** Orange→red→magenta sunset palette. (한국어) 선셋 팔레트. */
    val SunsetColors: List<Color> = listOf(
        Color(0xFFFF6D00),
        Color(0xFFFF1744),
        Color(0xFFD500F9),
        Color(0xFFFF6D00),
    )

    /** Green→sky-blue mint palette. (한국어) 민트 팔레트. */
    val MintColors: List<Color> = listOf(
        Color(0xFF00E676),
        Color(0xFF00B0FF),
        Color(0xFF00E676),
    )

    /**
     * Idle dim factor used by [interactiveStyle] so focus/press visibly "lights up".
     * (한국어) interactiveStyle에서 idle을 감쇠시키는 비율 — 포커스/눌림이 눈에 띄게 밝아진다.
     */
    const val IdleAlpha: Float = 0.7f

    /**
     * Material-like disabled alpha, applied when [AiGlowStyle.disabled] is null.
     * (한국어) disabled 미지정 시 적용되는 Material식 비활성 alpha.
     */
    const val DisabledAlpha: Float = 0.38f

    /**
     * An opinionated style where the glow brightens on focus/press and dims when idle.
     * Alpha-only differences between states animate smoothly without rebuilding the
     * draw cache, which is why the emphasis is expressed through [GlowConfig.alpha].
     *
     * (한국어) idle은 은은하게, 포커스/눌림엔 환하게 빛나는 기본 스타일.
     * 상태 간 차이를 alpha로만 표현해 draw 캐시 재생성 없이 부드럽게 전환됩니다.
     */
    fun interactiveStyle(config: GlowConfig = GlowConfig()): AiGlowStyle = AiGlowStyle(
        idle = config.copy(alpha = config.alpha * IdleAlpha),
        focused = config,
        pressed = config,
        hovered = config.copy(alpha = config.alpha * 0.85f),
    )

    /**
     * Text field colors tuned for glow: transparent borders (the gradient ring *is*
     * the border) and an opaque Material surface inside the outward-only halo.
     *
     * (한국어) 글로우 전용 텍스트필드 색입니다. 그라디언트 링이 테두리 역할을 하므로 기본
     * 테두리는 투명하게 두고, 바깥쪽 halo 안에는 불투명한 Material 표면을 유지합니다.
     */
    @Composable
    fun searchBarColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
    )

    /**
     * Text field colors for background-glow ("glass") mode: containers are fully
     * transparent so the gradient fill painted *behind* the text field shows through,
     * and stock borders stay hidden. [AiGlowSearchBar] switches to these automatically
     * when a `backgroundGlowStyle` is set.
     *
     * (한국어) 배경 글로우("글래스") 모드용 텍스트필드 색: 텍스트필드 뒤에 칠해지는
     * 그라디언트 채움이 비쳐 보이도록 컨테이너를 완전 투명으로 두고, 기본 테두리도
     * 숨깁니다. `backgroundGlowStyle` 지정 시 [AiGlowSearchBar]가 자동으로 사용합니다.
     */
    @Composable
    fun glassSearchBarColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )
}
