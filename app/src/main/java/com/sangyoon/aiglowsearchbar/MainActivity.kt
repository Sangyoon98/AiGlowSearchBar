package com.sangyoon.aiglowsearchbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sangyoon.aiglowsearchbar.ui.theme.AiGlowSearchBarTheme

/**
 * Entry point of the sample app: hosts [GlowPlaygroundScreen], an interactive
 * playground for every :aiglow customization option.
 * (한국어) 샘플 앱 진입점 — :aiglow의 모든 커스터마이징 옵션을 조작하는
 * 플레이그라운드를 띄웁니다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiGlowSearchBarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GlowPlaygroundScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GlowPlaygroundScreenPreview() {
    AiGlowSearchBarTheme {
        GlowPlaygroundScreen(modifier = Modifier.fillMaxSize())
    }
}
