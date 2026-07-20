package com.sangyoon.aiglow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the shared color and geometry contracts used by ring, halo, surface, and
 * bloom renderers.
 *
 * Why pure JVM tests: outline flattening belongs to Android's cached draw layer, but
 * the regressions live in pure perimeter-phase, center-mixing, and convexity logic.
 * Exercising those mappings directly proves every side advances by the same number of
 * pixels, the surface center stays phase-invariant, and custom inward geometry selects
 * the safe fallback without requiring screenshot timing or a device.
 *
 * (한국어) ring·halo·surface·bloom 렌더러가 공유하는 색상·geometry 계약을 검증합니다.
 * 외곽선 세분화는 Android draw 캐시에 맡기되, 둘레 phase·중심 혼합·볼록성 판별은 순수
 * 함수로 검사해 모든 변의 이동 거리, 중심의 phase 불변성, 커스텀 안쪽 geometry의 안전한
 * 폴백을 기기나 스크린샷 타이밍 없이 보장합니다.
 */
class PerimeterGradientTest {

    @Test
    fun `quarter phase advances the same distance at every rectangle position`() {
        val perimeter = 720f // 300 x 60 rectangle: 2 * (300 + 60)
        val quarterTurnDistance = perimeter / 4f

        listOf(0f, 120f, 299f, 330f, 650f, 719f).forEach { startDistance ->
            val advancedDistance = (startDistance + quarterTurnDistance) % perimeter
            assertEquals(
                perimeterGradientFraction(startDistance, perimeter, 0f),
                perimeterGradientFraction(advancedDistance, perimeter, 90f),
                1e-6f,
            )
        }
    }

    @Test
    fun `full turns wrap without a palette seam`() {
        val distance = 517f
        val perimeter = 720f
        val palette = closeGradientLoop(listOf(Color.Red, Color.Green, Color.Blue))

        assertEquals(
            perimeterGradientFraction(distance, perimeter, 0f),
            perimeterGradientFraction(distance, perimeter, 360f),
            1e-6f,
        )
        assertEquals(
            perimeterGradientFraction(distance, perimeter, 0f),
            perimeterGradientFraction(distance, perimeter, 720f),
            1e-6f,
        )
        assertEquals(cyclicColorAt(palette, 0f), cyclicColorAt(palette, 1f))
        assertEquals(
            cyclicColorAt(palette, perimeterGradientFraction(distance, perimeter, 0f)),
            cyclicColorAt(palette, perimeterGradientFraction(distance, perimeter, 360f)),
        )
    }

    @Test
    fun `phase normalization supports negative and oversized angles`() {
        assertEquals(0f, perimeterPhaseFraction(0f), 1e-6f)
        assertEquals(0.25f, perimeterPhaseFraction(90f), 1e-6f)
        assertEquals(0.75f, perimeterPhaseFraction(-90f), 1e-6f)
        assertEquals(0.25f, perimeterPhaseFraction(450f), 1e-6f)
    }

    @Test
    fun `degenerate perimeter resolves safely`() {
        assertEquals(0f, perimeterGradientFraction(10f, 0f, 90f), 0f)
        assertEquals(0f, perimeterGradientFraction(10f, Float.NaN, 90f), 0f)
    }

    @Test
    fun `native blur outset follows the skia kernel support`() {
        assertEquals(0f, nativeBlurMaskOutsetPx(0f), 0f)
        assertEquals(4f, nativeBlurMaskOutsetPx(1f), 0f)
        assertEquals(30f, nativeBlurMaskOutsetPx(16f), 0f)
    }

    @Test
    fun `surface center mixes the cyclic palette without weighting its closing stop twice`() {
        val open = listOf(Color.Red, Color.Blue)
        val closed = listOf(Color.Red, Color.Blue, Color.Red)

        assertEquals(0xFF800080.toInt(), mixedGradientColorArgb(open))
        assertEquals(mixedGradientColorArgb(open), mixedGradientColorArgb(closed))
        assertEquals(Color.Green.toArgb(), mixedGradientColorArgb(listOf(Color.Green)))
    }

    @Test
    fun `surface center uses premultiplied colors for translucent palettes`() {
        assertEquals(
            0x800000FF.toInt(),
            mixedGradientColorArgb(listOf(Color.Red.copy(alpha = 0f), Color.Blue)),
        )
    }

    @Test
    fun `surface phase changes its perimeter rows but never its mixed center row`() {
        val palette = closeGradientLoop(listOf(Color.Red, Color.Blue))
        val fractions = floatArrayOf(0f, 0.5f, 1f)
        val centerColor = mixedGradientColorArgb(palette)
        val target = IntArray(fractions.size * 3) { centerColor }
        val centerRowStart = fractions.size * 2
        val expectedCenterRow = IntArray(fractions.size) { centerColor }

        applyPerimeterPhaseColors(palette, fractions, 2, target, 0f)
        val zeroDegreeRows = target.copyOfRange(0, centerRowStart)
        assertArrayEquals(expectedCenterRow, target.copyOfRange(centerRowStart, target.size))

        applyPerimeterPhaseColors(palette, fractions, 2, target, 90f)
        assertNotEquals(zeroDegreeRows.toList(), target.copyOfRange(0, centerRowStart).toList())
        assertArrayEquals(expectedCenterRow, target.copyOfRange(centerRowStart, target.size))

        applyPerimeterPhaseColors(palette, fractions, 2, target, 360f)
        assertArrayEquals(zeroDegreeRows, target.copyOfRange(0, centerRowStart))
        assertArrayEquals(expectedCenterRow, target.copyOfRange(centerRowStart, target.size))
    }

    @Test
    fun `inward carrier distinguishes convex and concave contour geometry`() {
        assertTrue(
            polygonIsConvex(
                floatArrayOf(
                    0f, 0f,
                    4f, 0f,
                    4f, 2f,
                    0f, 2f,
                ),
            ),
        )
        assertFalse(
            polygonIsConvex(
                floatArrayOf(
                    0f, 0f,
                    4f, 0f,
                    2f, 1f,
                    4f, 2f,
                    0f, 2f,
                ),
            ),
        )
    }
}
