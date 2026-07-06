package com.sangyoon.aiglow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 회전하는 AI 글로우 테두리를 두른 검색 바.
 *
 * 레이어 설계(아래→위): [블러 halo] → [OutlinedTextField] → [선명한 그라디언트 링].
 * 왜 이렇게 쌓는가: `Modifier.blur()`는 노드가 그리는 콘텐츠 전체에 RenderEffect를 걸기
 * 때문에 텍스트필드에 직접 적용하면 글자까지 흐려진다. 그래서 번짐은 글로우만 그리는
 * 빈 레이어에 격리해 적용하고(공식 Graphics Modifiers 문서의 blur 동작 방식), 선명한
 * 링은 컨테이너 배경 위에 얹어 두 레이어가 합쳐진 "빛나는 테두리"를 만든다.
 * 두 레이어는 [rememberGlowAngle] 하나가 만든 **단일 회전 상태를 공유**하므로 어긋날 수 없다.
 *
 * 왜 상태 호이스팅 시그니처(query/onQueryChange)인가: 컴포넌트가 검색어를 소유하지 않아야
 * 호출자가 ViewModel 등 어디에든 상태를 둘 수 있고, 이 함수 자체는 stateless로 남아
 * 재사용과 테스트가 쉬워진다(공식 State hoisting 가이드).
 *
 * API 31 미만 기기에서는 `Modifier.blur()`가 무시되므로(공식 문서 명시) halo 레이어가
 * 선명한 링으로 대체 렌더링되지만, 그라디언트 테두리라는 핵심 시각은 그대로 유지된다.
 *
 * @param query 현재 검색어. 호출자가 소유한다.
 * @param onQueryChange 검색어 변경 콜백.
 * @param modifier 배치용 Modifier. 관례에 따라 첫 optional 파라미터로 둔다.
 * @param glowConfig 글로우 커스터마이징. `glowConfig.shape`가 텍스트필드의 shape에도
 *   그대로 적용되어 글로우와 컨테이너 외곽선이 항상 일치한다.
 */
@Composable
fun AiGlowSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    glowConfig: GlowConfig = GlowConfig(),
) {
    // 단일 회전 상태를 두 글로우 레이어가 공유한다 — 인스턴스별로는 독립, 레이어끼리는 동기화.
    val angle = rememberGlowAngle(glowConfig.rotationDuration)

    // halo는 같은 config의 copy()로 두께만 키운다: 불변 data class 커스터마이징 관례의 실례.
    val haloConfig = remember(glowConfig) {
        glowConfig.copy(strokeWidth = glowConfig.strokeWidth * 2)
    }
    // Modifier 인스턴스를 remember로 고정해 검색어 입력(recomposition)마다
    // drawWithCache 캐시가 버려지는 것을 막는다.
    val haloRing = remember(haloConfig, angle) {
        Modifier.glowRing(haloConfig) { angle.value }
    }
    val crispRing = remember(glowConfig, angle) {
        Modifier.glowRing(glowConfig) { angle.value }
    }

    Box(modifier = modifier) {
        if (glowConfig.blurRadius > 0.dp) {
            Box(
                Modifier
                    .matchParentSize()
                    // Unbounded: 글로우가 검색 바 경계 밖으로 자연스럽게 번지도록 클리핑을 끈다.
                    .blur(glowConfig.blurRadius, BlurredEdgeTreatment.Unbounded)
                    .then(haloRing),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = glowConfig.shape,
            colors = OutlinedTextFieldDefaults.colors(
                // 기본 테두리는 끈다: 테두리 역할은 위에 얹는 그라디언트 링이 대신한다.
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                // 컨테이너를 불투명하게 채워 halo의 안쪽 절반을 가리면 "바깥으로만 빛나는" 효과가 된다.
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        // draw 전용 레이어라 semantics/pointer 입력이 없으므로 텍스트필드 터치를 가로채지 않는다.
        Box(
            Modifier
                .matchParentSize()
                .then(crispRing),
        )
    }
}
