package com.sangyoon.aiglow

import android.graphics.Matrix
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 콘텐츠 뒤(behind)에 회전하는 sweep-gradient 글로우 링을 그리는 Modifier 확장.
 *
 * 왜 `Modifier.composed`가 아니라 @Composable modifier factory인가:
 * 회전 각도는 composition 생명주기에 묶인 상태(`rememberInfiniteTransition`)여야 하는데,
 * `composed {}`는 materialize 시점마다 람다를 재실행해 skip 최적화를 무력화하는 비용이
 * 알려져 있어(Compose API Guidelines의 composed 사용 자제 권고) composable factory를
 * 택했다. 이 함수는 **호출 지점(call site)마다 독립적인 composition slot**에 transition을
 * 저장하므로, 화면에 aiGlow를 몇 개를 배치하든 각 인스턴스의 회전 각도가 서로 간섭할 수
 * 없다 — 전역/companion 상태가 없다는 것이 구조적으로 보장된다.
 *
 * 왜 반환 Modifier를 remember로 감싸는가: 이 함수를 호출한 컴포저블이 (예: 검색어 입력으로)
 * recomposition될 때마다 새 drawWithCache 람다가 만들어지면 Modifier 동등성이 깨져 draw
 * 캐시가 매번 버려진다. `remember(glowConfig, angle)`로 Modifier 인스턴스 자체를 고정해
 * config가 실제로 바뀔 때만 노드가 갱신되게 한다(Compose 성능 가이드의 "비용이 큰 객체는
 * remember로 재사용" 원칙).
 *
 * 번짐(blur)을 여기서 적용하지 않는 이유: `Modifier.blur()`는 해당 노드가 그리는 **모든
 * 콘텐츠**를 블러시키므로, 텍스트필드 같은 실제 콘텐츠에 직접 걸면 내용까지 흐려진다.
 * 번짐이 필요하면 [AiGlowSearchBar]처럼 글로우 전용 빈 레이어에
 * `Modifier.blur(radius, BlurredEdgeTreatment.Unbounded)`를 선행 체이닝한 뒤 aiGlow를 적용한다.
 *
 * @param glowConfig 글로우의 색/두께/회전 주기/모양. 불변이므로 `copy()`로 커스터마이징한다.
 */
@Composable
fun Modifier.aiGlow(glowConfig: GlowConfig): Modifier {
    val angle = rememberGlowAngle(glowConfig.rotationDuration)
    val ring = remember(glowConfig, angle) {
        Modifier.glowRing(glowConfig) { angle.value }
    }
    return this then ring
}

/**
 * 0°→360°를 무한 반복하는 회전 각도 상태를 만든다.
 *
 * 왜 분리했는가: "상태 생성(composition 단계)"과 "그리기(draw 단계)"를 층으로 나누면,
 * [AiGlowSearchBar]처럼 여러 글로우 레이어(선명한 링 + 블러 halo)가 **하나의 transition을
 * 공유**해 완벽히 동기화된 채 렌더링될 수 있다. transition은 이 컴포저블이 composition을
 * 떠나는 순간 함께 정리되므로 누수나 전역 상태가 없다(공식 애니메이션 가이드의
 * rememberInfiniteTransition 생명주기 규약).
 */
@Composable
internal fun rememberGlowAngle(rotationDurationMillis: Int): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "AiGlowRotation")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "AiGlowAngle",
    )
}

/**
 * 순수 draw 전용 글로우 링 Modifier. composition 상태를 만들지 않는다.
 *
 * 왜 drawWithCache인가: [Outline] 생성(Path 연산), [SweepGradientShader] 할당, [Stroke]
 * 객체는 프레임마다 만들기엔 비싼 객체다. drawWithCache는 크기나 상태가 바뀌지 않는 한
 * 이들을 캐시에 유지해 매 프레임 재할당을 막는다(공식 Graphics Modifiers 문서의
 * drawWithCache 권장 사례).
 *
 * 왜 각도를 값이 아닌 `angleProvider` 람다로 받는가: State 읽기를 onDrawBehind 내부로
 * 지연(defer)시키면 각도가 바뀔 때 **draw phase만** 무효화되고 composition/layout은
 * 전혀 실행되지 않는다(공식 성능 가이드 "읽기를 최대한 늦춰라" + Compose 3단계 phase 모델).
 * 매 프레임 60~120회 갱신되는 값이므로 이 차이가 성능의 핵심이다.
 *
 * 왜 캔버스가 아닌 shader의 local matrix를 회전시키는가: 캔버스 rotate는 Outline(모양)
 * 자체를 함께 돌려 사각형/캡슐이 기울어져 버린다. shader matrix 회전은 그라디언트 색상만
 * 외곽선 위에서 흐르게 하고 모양은 고정한다.
 */
internal fun Modifier.glowRing(
    glowConfig: GlowConfig,
    angleProvider: () -> Float,
): Modifier = drawWithCache {
    // seam(이음새) 방지: sweep gradient는 360° 지점에서 첫 색으로 되돌아와야 자연스럽다.
    val sweepColors = when {
        glowConfig.colors.size == 1 -> glowConfig.colors + glowConfig.colors
        glowConfig.colors.first() != glowConfig.colors.last() -> glowConfig.colors + glowConfig.colors.first()
        else -> glowConfig.colors
    }
    val outline = glowConfig.shape.createOutline(size, layoutDirection, this)
    val center = size.center
    val shader = SweepGradientShader(center = center, colors = sweepColors)
    val brush = ShaderBrush(shader)
    val stroke = Stroke(width = glowConfig.strokeWidth.toPx())
    val shaderMatrix = Matrix()

    onDrawBehind {
        // State 읽기가 여기(draw phase)에서만 일어나므로 회전 중 recomposition은 0회다.
        shaderMatrix.setRotate(angleProvider(), center.x, center.y)
        shader.setLocalMatrix(shaderMatrix)
        drawOutline(outline = outline, brush = brush, style = stroke)
    }
}
