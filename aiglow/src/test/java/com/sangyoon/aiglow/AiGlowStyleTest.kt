package com.sangyoon.aiglow

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies interaction-state resolution: priority order, idle fallbacks and the
 * derived disabled state. (한국어) 상태 결정 로직(우선순위, idle 폴백, disabled 파생)을
 * 검증합니다.
 */
class AiGlowStyleTest {

    private val idle = GlowConfig(alpha = 1f)
    private val focusedConfig = GlowConfig(colors = listOf(Color.Red, Color.Blue))
    private val pressedConfig = GlowConfig(colors = listOf(Color.Green, Color.Cyan))

    @Test
    fun `pressed wins over focused and hovered`() {
        val style = AiGlowStyle(idle = idle, focused = focusedConfig, pressed = pressedConfig)

        val resolved = style.resolve(enabled = true, focused = true, pressed = true, hovered = true)

        assertEquals(pressedConfig, resolved)
    }

    @Test
    fun `pressed falls back to focused then idle`() {
        val withFocused = AiGlowStyle(idle = idle, focused = focusedConfig)
        assertEquals(
            focusedConfig,
            withFocused.resolve(enabled = true, focused = false, pressed = true, hovered = false),
        )

        val bare = AiGlowStyle(idle = idle)
        assertEquals(
            idle,
            bare.resolve(enabled = true, focused = false, pressed = true, hovered = false),
        )
    }

    @Test
    fun `focused falls back to idle when not provided`() {
        val style = AiGlowStyle(idle = idle)

        val resolved = style.resolve(enabled = true, focused = true, pressed = false, hovered = false)

        assertEquals(idle, resolved)
    }

    @Test
    fun `disabled defaults to dimmed static idle`() {
        val style = AiGlowStyle(idle = idle)

        val resolved = style.resolve(enabled = false, focused = false, pressed = false, hovered = false)

        assertEquals(idle.alpha * AiGlowDefaults.DisabledAlpha, resolved.alpha, 1e-6f)
        assertFalse(resolved.animated)
    }

    @Test
    fun `explicit disabled config is used as-is`() {
        val disabledConfig = GlowConfig(alpha = 0.5f, animated = true)
        val style = AiGlowStyle(idle = idle, disabled = disabledConfig)

        val resolved = style.resolve(enabled = false, focused = true, pressed = true, hovered = true)

        assertEquals(disabledConfig, resolved)
    }

    @Test
    fun `interactiveStyle dims idle and keeps focus and press at full strength`() {
        val base = GlowConfig(alpha = 1f)

        val style = AiGlowDefaults.interactiveStyle(base)

        assertEquals(AiGlowDefaults.IdleAlpha, style.idle.alpha, 1e-6f)
        assertEquals(1f, style.focused!!.alpha, 1e-6f)
        assertEquals(1f, style.pressed!!.alpha, 1e-6f)
    }

    @Test
    fun `pinnedToShape overrides shape on every non-null state, leaves everything else untouched`() {
        val style = AiGlowStyle(
            idle = GlowConfig(shape = CircleShape, alpha = 1f),
            focused = GlowConfig(shape = CircleShape, colors = listOf(Color.Red, Color.Blue)),
            pressed = null,
            hovered = GlowConfig(shape = CircleShape, alpha = 0.5f),
            disabled = null,
        )
        val targetShape = RoundedCornerShape(12.dp)

        val pinned = style.pinnedToShape(targetShape)

        assertEquals(targetShape, pinned.idle.shape)
        assertEquals(targetShape, pinned.focused!!.shape)
        assertEquals(targetShape, pinned.hovered!!.shape)
        assertNull(pinned.pressed)
        assertNull(pinned.disabled)
        // Non-shape fields survive untouched. (한국어) shape 외 필드는 그대로 유지된다.
        assertEquals(1f, pinned.idle.alpha, 1e-6f)
        assertEquals(listOf(Color.Red, Color.Blue), pinned.focused!!.colors)
        assertEquals(0.5f, pinned.hovered!!.alpha, 1e-6f)
    }
}
