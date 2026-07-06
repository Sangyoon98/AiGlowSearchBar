package com.sangyoon.aiglow

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * GlowConfig의 불변 계약(copy 기반 커스터마이징, 구조적 동등성, 유효성 검증)을 검증한다.
 * 렌더링 없는 순수 값 로직이므로 JVM 단위 테스트로 충분하다.
 */
class GlowConfigTest {

    @Test
    fun `copy로 shape만 바꾸면 나머지 값은 그대로 유지된다`() {
        val base = GlowConfig()
        val squared = base.copy(shape = RoundedCornerShape(12.dp))

        assertEquals(base.colors, squared.colors)
        assertEquals(base.strokeWidth, squared.strokeWidth)
        assertEquals(base.blurRadius, squared.blurRadius)
        assertEquals(base.rotationDuration, squared.rotationDuration)
        assertNotEquals(base.shape, squared.shape)
    }

    @Test
    fun `같은 값으로 만든 두 config는 구조적으로 동등하다 - stability 계약의 전제`() {
        val a = GlowConfig(colors = listOf(Color.Red, Color.Blue), rotationDuration = 1_000)
        val b = GlowConfig(colors = listOf(Color.Red, Color.Blue), rotationDuration = 1_000)

        assertEquals(a, b)
    }

    @Test
    fun `빈 색상 리스트는 거부된다`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(colors = emptyList())
        }
    }

    @Test
    fun `rotationDuration이 0 이하이면 거부된다`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(rotationDuration = 0)
        }
    }

    @Test
    fun `음수 strokeWidth와 blurRadius는 거부된다`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(strokeWidth = (-1).dp)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GlowConfig(blurRadius = (-1).dp)
        }
    }
}
