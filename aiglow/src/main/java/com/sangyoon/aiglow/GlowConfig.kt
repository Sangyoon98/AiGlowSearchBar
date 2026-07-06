package com.sangyoon.aiglow

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * aiGlow의 기본 그라디언트 팔레트.
 *
 * 왜 top-level private val인가: companion object에 두면 "전역 상태" 관례가 생기기 쉽고,
 * 이 라이브러리는 companion object 내 상태(특히 mutable)를 금지하는 규칙을 따르므로
 * 파일 스코프의 불변 값으로 격리한다. sweep gradient의 이음새(seam)가 보이지 않도록
 * 시작 색으로 자연스럽게 되돌아오는 순환 팔레트로 구성했다.
 */
private val DefaultGlowColors: List<Color> = listOf(
    Color(0xFF4285F4), // blue
    Color(0xFF9B72CB), // purple
    Color(0xFFD96570), // rose
    Color(0xFF9B72CB), // purple (blue로 되돌아가는 중간 단계)
)

/**
 * [aiGlow] 효과의 모든 시각 파라미터를 담는 불변 설정 객체.
 *
 * 왜 `@Immutable` data class인가: Compose 컴파일러는 `List`나 `Shape`(인터페이스) 같은
 * 타입을 스스로 안정(stable)하다고 추론하지 못한다. `@Immutable`은 "이 객체의 모든 공개
 * 프로퍼티는 생성 후 절대 변하지 않는다"는 개발자 계약을 컴파일러에 알려, 같은 값이
 * 전달되는 한 이 config를 읽는 컴포저블/Modifier가 recomposition을 건너뛸 수 있게 한다
 * (Android 공식 Stability 가이드: developer.android.com/develop/ui/compose/performance/stability).
 *
 * 왜 Builder가 아니라 data class + copy()인가: var 필드를 노출하는 Builder는 위의 불변
 * 계약을 깨뜨려 stability 추론을 무너뜨린다. `copy()`는 구조적 동등성(equals)이 유지되는
 * 새 불변 인스턴스를 만들므로, 일부 값만 바꿔도 나머지 파라미터의 skip 최적화가 보존된다.
 *
 * @property colors sweep gradient에 사용할 색 목록. 계약상 불변 리스트로 취급해야 하며
 *   (외부에서 변형 가능한 리스트를 넘긴 뒤 수정하면 `@Immutable` 계약 위반),
 *   첫 색과 끝 색이 다르면 이음새 제거를 위해 그리기 시점에 첫 색이 자동으로 덧붙는다.
 * @property strokeWidth 글로우 링(테두리)의 두께. 픽셀이 아닌 [Dp]로 받아 밀도 독립성을 유지한다.
 * @property blurRadius 글로우가 바깥으로 번지는 반경. 0.dp면 번짐 없이 선명한 링만 그린다.
 *   `Modifier.blur()` 기반이므로 API 31 미만 기기에서는 번짐이 생략되고 링만 표시된다.
 * @property rotationDuration 그라디언트가 한 바퀴(360°) 도는 데 걸리는 시간(ms).
 * @property shape 글로우와 텍스트필드가 공유하는 외곽선. 커스텀 enum이 아닌 Compose 표준
 *   [Shape] 인터페이스를 그대로 받아, [CircleShape]·RoundedCornerShape는 물론 사용자가
 *   정의한 어떤 Shape와도 조합될 수 있게 한다(개방-폐쇄 원칙).
 */
@Immutable
data class GlowConfig(
    val colors: List<Color> = DefaultGlowColors,
    val strokeWidth: Dp = 2.dp,
    val blurRadius: Dp = 16.dp,
    val rotationDuration: Int = 4_000,
    val shape: Shape = CircleShape,
) {
    init {
        require(colors.isNotEmpty()) { "GlowConfig.colors에는 최소 1개 이상의 색이 필요합니다." }
        require(rotationDuration > 0) { "rotationDuration은 0보다 큰 밀리초 값이어야 합니다." }
        require(strokeWidth >= 0.dp) { "strokeWidth는 음수가 될 수 없습니다." }
        require(blurRadius >= 0.dp) { "blurRadius는 음수가 될 수 없습니다." }
    }
}
