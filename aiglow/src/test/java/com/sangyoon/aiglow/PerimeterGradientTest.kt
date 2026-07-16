package com.sangyoon.aiglow

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the arc-length contract used by the edge ring and halo renderers.
 *
 * Why pure JVM tests: outline flattening belongs to Android's cached draw layer, but
 * the regression was caused by mapping animation phase to polar angle instead of
 * distance. Exercising that mapping directly proves every side advances by the same
 * number of pixels without requiring screenshot timing or a device.
 *
 * (한국어) 테두리 링과 halo 렌더러가 공유하는 둘레 길이 계약을 검증합니다. 외곽선 세분화는
 * Android draw 캐시에 맡기되, 회귀 원인이었던 phase→거리 매핑은 순수 함수로 직접 검사해
 * 기기나 스크린샷 타이밍 없이 모든 변이 같은 픽셀 거리만큼 이동함을 보장합니다.
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
}
