package com.sangyoon.aiglowsearchbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sangyoon.aiglow.AiGlowSearchBar
import com.sangyoon.aiglow.GlowConfig
import com.sangyoon.aiglow.aiGlow
import com.sangyoon.aiglowsearchbar.ui.theme.AiGlowSearchBarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiGlowSearchBarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GlowDemoScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * :aiglow 검증용 데모.
 * - 검색 바 2개를 서로 다른 rotationDuration(4초 vs 1.5초)으로 동시에 배치해
 *   애니메이션 상태가 인스턴스별로 독립임을 눈으로 확인한다(속도가 다르면 공유 상태일 수 없다).
 * - 버튼으로 GlowConfig.copy(shape = ...)만 바꿔 캡슐형 ↔ 사각형 전환을 확인한다.
 * - 마지막 카드는 Modifier.aiGlow를 임의 컴포저블에 단독 적용한 재사용성 예시다.
 */
@Composable
fun GlowDemoScreen(modifier: Modifier = Modifier) {
    var firstQuery by rememberSaveable { mutableStateOf("") }
    var secondQuery by rememberSaveable { mutableStateOf("") }
    var isCapsule by rememberSaveable { mutableStateOf(true) }

    val shape = if (isCapsule) CircleShape else RoundedCornerShape(12.dp)

    // GlowConfig는 @Immutable이므로 shape가 바뀔 때만 새 인스턴스가 만들어지고,
    // 검색어 입력으로 인한 recomposition에는 동일 인스턴스가 재사용된다.
    val geminiConfig = remember(shape) { GlowConfig(shape = shape) }
    val auroraConfig = remember(shape) {
        GlowConfig(
            colors = listOf(
                Color(0xFF00E5FF),
                Color(0xFF2979FF),
                Color(0xFFD500F9),
            ),
            strokeWidth = 3.dp,
            blurRadius = 24.dp,
            rotationDuration = 1_500,
            shape = shape,
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Text(text = "AiGlow 데모", style = MaterialTheme.typography.headlineSmall)

        AiGlowSearchBar(
            query = firstQuery,
            onQueryChange = { firstQuery = it },
            modifier = Modifier.fillMaxWidth(),
            glowConfig = geminiConfig,
        )

        AiGlowSearchBar(
            query = secondQuery,
            onQueryChange = { secondQuery = it },
            modifier = Modifier.fillMaxWidth(),
            glowConfig = auroraConfig,
        )

        Button(onClick = { isCapsule = !isCapsule }) {
            Text(text = if (isCapsule) "사각형으로 전환" else "캡슐형으로 전환")
        }

        val cardShape = RoundedCornerShape(16.dp)
        val cardConfig = remember {
            GlowConfig(
                shape = RoundedCornerShape(16.dp),
                rotationDuration = 6_000,
                blurRadius = 0.dp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .aiGlow(cardConfig)
                .background(MaterialTheme.colorScheme.surfaceVariant, cardShape)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Modifier.aiGlow 단독 적용 (blur 없이)")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GlowDemoScreenPreview() {
    AiGlowSearchBarTheme {
        GlowDemoScreen(modifier = Modifier.fillMaxSize())
    }
}
