package com.sangyoon.aiglow

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Verifies the immutability contract of [GlowConfig]: copy-based customization,
 * structural equality (the premise of the `@Immutable` stability contract) and
 * constructor validation. Pure value logic — a JVM unit test is sufficient.
 *
 * (한국어) GlowConfig의 불변 계약(copy 커스터마이징, 구조적 동등성, 유효성 검증)을
 * 검증합니다. 렌더링 없는 순수 값 로직이라 JVM 단위 테스트로 충분합니다.
 */
class GlowConfigTest {

    @Test
    fun `copy changing shape keeps every other value`() {
        val base = GlowConfig()
        val squared = base.copy(shape = RoundedCornerShape(12.dp))

        assertEquals(base.colors, squared.colors)
        assertEquals(base.strokeWidth, squared.strokeWidth)
        assertEquals(base.blurRadius, squared.blurRadius)
        assertEquals(base.rotationDuration, squared.rotationDuration)
        assertEquals(base.alpha, squared.alpha)
        assertNotEquals(base.shape, squared.shape)
    }

    @Test
    fun `configs built from identical values are structurally equal`() {
        val a = GlowConfig(colors = listOf(Color.Red, Color.Blue), rotationDuration = 1_000)
        val b = GlowConfig(colors = listOf(Color.Red, Color.Blue), rotationDuration = 1_000)

        assertEquals(a, b)
    }

    @Test
    fun `halo values fall back to base values when null`() {
        val config = GlowConfig(strokeWidth = 3.dp)
        assertEquals(config.colors, config.resolvedHaloColors)
        assertEquals(6.dp, config.resolvedHaloStrokeWidth)

        val custom = GlowConfig(
            haloColors = listOf(Color.Red, Color.Blue),
            haloStrokeWidth = 10.dp,
        )
        assertEquals(listOf(Color.Red, Color.Blue), custom.resolvedHaloColors)
        assertEquals(10.dp, custom.resolvedHaloStrokeWidth)
    }

    @Test
    fun `cyclic color loop is closed to hide the seam`() {
        // Open palette: first color appended. (한국어) 열린 팔레트엔 첫 색이 덧붙는다.
        val open = listOf(Color.Red, Color.Blue)
        assertEquals(listOf(Color.Red, Color.Blue, Color.Red), closeGradientLoop(open))

        // Already closed: unchanged. (한국어) 이미 닫힌 팔레트는 그대로.
        val closed = listOf(Color.Red, Color.Blue, Color.Red)
        assertEquals(closed, closeGradientLoop(closed))

        // Single color: duplicated so cyclic mesh interpolation still has one segment.
        // (한국어) 단색도 순환 mesh 보간 구간이 하나 생기도록 두 번 둔다.
        assertEquals(listOf(Color.Red, Color.Red), closeGradientLoop(listOf(Color.Red)))
    }

    @Test
    fun `empty colors list is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(colors = emptyList())
        }
    }

    @Test
    fun `empty haloColors list is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(haloColors = emptyList())
        }
    }

    @Test
    fun `non-positive rotationDuration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(rotationDuration = 0)
        }
    }

    @Test
    fun `negative dimensions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(strokeWidth = (-1).dp)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(blurRadius = (-1).dp)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(haloStrokeWidth = (-1).dp)
        }
    }

    @Test
    fun `haloDirection defaults to Outward and copy changes only it`() {
        val base = GlowConfig()
        assertEquals(HaloDirection.Outward, base.haloDirection)

        val inward = base.copy(haloDirection = HaloDirection.Inward)
        assertEquals(HaloDirection.Inward, inward.haloDirection)
        // Everything else survives untouched. (한국어) 나머지 필드는 그대로 유지된다.
        assertEquals(base.colors, inward.colors)
        assertEquals(base.strokeWidth, inward.strokeWidth)
        assertEquals(base.blurRadius, inward.blurRadius)
        assertEquals(base.shape, inward.shape)
    }

    @Test
    fun `alpha outside 0 to 1 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(alpha = -0.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(alpha = 1.1f)
        }
    }
}
