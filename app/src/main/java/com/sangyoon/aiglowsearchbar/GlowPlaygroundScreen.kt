@file:OptIn(ExperimentalLayoutApi::class)

package com.sangyoon.aiglowsearchbar

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangyoon.aiglow.AiGlowBox
import com.sangyoon.aiglow.AiGlowDefaults
import com.sangyoon.aiglow.AiGlowFloatingActionButton
import com.sangyoon.aiglow.AiGlowSearchBar
import com.sangyoon.aiglow.AiGlowStyle
import com.sangyoon.aiglow.GlowConfig
import com.sangyoon.aiglow.HaloDirection
import kotlin.math.roundToInt

/** Preset ring/halo palettes offered by the playground. (한국어) 프리셋 팔레트 목록. */
private val PresetPalettes: List<Pair<String, List<Color>>> = listOf(
    "Gemini" to AiGlowDefaults.GeminiColors,
    "Aurora" to AiGlowDefaults.AuroraColors,
    "Sunset" to AiGlowDefaults.SunsetColors,
    "Mint" to AiGlowDefaults.MintColors,
)

/** Easing options; the label + "Easing" is also the real symbol name shown in the
 * generated code. (한국어) 라벨 + "Easing"이 실제 심볼명이라 코드 출력에도 그대로 쓴다. */
private val EasingOptions: List<Pair<String, Easing>> = listOf(
    "Linear" to LinearEasing,
    "FastOutSlowIn" to FastOutSlowInEasing,
    "LinearOutSlowIn" to LinearOutSlowInEasing,
    "FastOutLinearIn" to FastOutLinearInEasing,
)

/** Swatches available for building a custom palette. (한국어) 커스텀 팔레트용 색상 견본. */
private val SwatchColors: List<Color> = listOf(
    Color(0xFFFF1744), Color(0xFFFF4081), Color(0xFFD500F9), Color(0xFF7C4DFF),
    Color(0xFF536DFE), Color(0xFF2979FF), Color(0xFF00B0FF), Color(0xFF00E5FF),
    Color(0xFF1DE9B6), Color(0xFF00E676), Color(0xFFFFEA00), Color(0xFFFF9100),
)

private const val CustomPaletteIndex = 4

/**
 * Interactive playground exposing every customization knob of the :aiglow library:
 * component choice, border-ring glow (palette presets + ordered custom swatches,
 * stroke width, halo colors/width, blur radius), background surface glow (own
 * palette, opacity, bloom, rotation speed), corner radius, rotation
 * duration/easing/on-off, alpha, enabled state, state-aware styling, and a twin
 * preview that proves animation independence. The current selection is also rendered
 * as copy-pastable code.
 *
 * (한국어) :aiglow의 모든 커스터마이징 항목을 실시간으로 조작하는 플레이그라운드입니다.
 * 테두리 링 글로우(팔레트/두께/halo/blur)와 배경 표면 글로우(전용 팔레트/투명도/bloom/
 * 회전 속도)를 각각 켜고 끌 수 있으며, 트윈 프리뷰로 인스턴스 간 애니메이션 독립성을
 * 확인하고 현재 설정을 복사 가능한 코드로 보여줍니다.
 */
@Composable
fun GlowPlaygroundScreen(modifier: Modifier = Modifier) {
    // --- Component & component-specific options ---
    var componentIndex by rememberSaveable { mutableStateOf(0) }
    var showLeadingIcon by rememberSaveable { mutableStateOf(true) }
    var showClearButton by rememberSaveable { mutableStateOf(true) }
    var boxBackground by rememberSaveable { mutableStateOf(true) }
    var showTwin by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var twinQuery by rememberSaveable { mutableStateOf("") }

    // --- Shape ---
    var cornerRadius by rememberSaveable { mutableStateOf(28f) }

    // --- Border ring glow ---
    var borderGlow by rememberSaveable { mutableStateOf(true) }
    var ringPaletteIndex by rememberSaveable { mutableStateOf(0) }
    var strokeWidth by rememberSaveable { mutableStateOf(2f) }
    val customColors = remember { mutableStateListOf(Color(0xFF2979FF), Color(0xFFD500F9)) }

    // --- Ring halo ---
    var blurRadius by rememberSaveable { mutableStateOf(16f) }
    var haloSameColors by rememberSaveable { mutableStateOf(true) }
    var haloPaletteIndex by rememberSaveable { mutableStateOf(1) }
    var haloAutoWidth by rememberSaveable { mutableStateOf(true) }
    var haloWidth by rememberSaveable { mutableStateOf(4f) }
    var haloDirectionIndex by rememberSaveable { mutableStateOf(0) }

    // --- Background surface glow ---
    var backgroundGlow by rememberSaveable { mutableStateOf(false) }
    var bgPaletteIndex by rememberSaveable { mutableStateOf(1) }
    var bgAlpha by rememberSaveable { mutableStateOf(0.35f) }
    var bgBloom by rememberSaveable { mutableStateOf(20f) }
    var bgDuration by rememberSaveable { mutableStateOf(6_000f) }

    // --- Animation ---
    var rotationDuration by rememberSaveable { mutableStateOf(4_000f) }
    var animated by rememberSaveable { mutableStateOf(true) }
    var easingIndex by rememberSaveable { mutableStateOf(0) }

    // --- State ---
    var alpha by rememberSaveable { mutableStateOf(1f) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var stateAware by rememberSaveable { mutableStateOf(true) }

    // GlowConfig requires a non-empty palette, so an emptied custom palette falls back.
    // (한국어) GlowConfig는 빈 팔레트를 거부하므로 커스텀이 비면 기본 팔레트로 폴백.
    val ringColors = if (ringPaletteIndex == CustomPaletteIndex) {
        customColors.toList().ifEmpty { AiGlowDefaults.GeminiColors }
    } else {
        PresetPalettes[ringPaletteIndex].second
    }

    val shape = RoundedCornerShape(cornerRadius.dp)
    val easing = EasingOptions[easingIndex].second

    val ringConfig = GlowConfig(
        colors = ringColors,
        strokeWidth = strokeWidth.dp,
        blurRadius = blurRadius.dp,
        rotationDuration = rotationDuration.roundToInt().coerceAtLeast(1),
        shape = shape,
        haloColors = if (haloSameColors) null else PresetPalettes[haloPaletteIndex].second,
        haloStrokeWidth = if (haloAutoWidth) null else haloWidth.dp,
        alpha = alpha.coerceIn(0f, 1f),
        animated = animated,
        easing = easing,
        haloDirection = HaloDirection.entries[haloDirectionIndex],
    )
    val bgConfig = GlowConfig(
        colors = PresetPalettes[bgPaletteIndex].second,
        blurRadius = bgBloom.dp,
        rotationDuration = bgDuration.roundToInt().coerceAtLeast(1),
        shape = shape,
        alpha = bgAlpha.coerceIn(0f, 1f),
        animated = animated,
        easing = easing,
    )

    fun styleOf(config: GlowConfig): AiGlowStyle =
        if (stateAware) AiGlowDefaults.interactiveStyle(config) else AiGlowStyle(idle = config)

    val ringStyle = if (borderGlow) styleOf(ringConfig) else null
    val bgStyle = if (backgroundGlow) styleOf(bgConfig) else null

    // Twin runs at half the duration: visibly different speeds prove that animation
    // state is per-instance. (한국어) 트윈은 절반 주기로 회전 — 속도가 다르면 상태 공유가
    // 불가능하다는 증거.
    val twinRingStyle = if (borderGlow) {
        styleOf(ringConfig.copy(rotationDuration = (ringConfig.rotationDuration / 2).coerceAtLeast(250)))
    } else {
        null
    }
    val twinBgStyle = if (backgroundGlow) {
        styleOf(bgConfig.copy(rotationDuration = (bgConfig.rotationDuration / 2).coerceAtLeast(250)))
    } else {
        null
    }

    Column(modifier = modifier) {
        PreviewPane(
            componentIndex = componentIndex,
            ringStyle = ringStyle,
            bgStyle = bgStyle,
            twinRingStyle = if (showTwin) twinRingStyle else null,
            twinBgStyle = if (showTwin) twinBgStyle else null,
            showTwinInstance = showTwin,
            enabled = enabled,
            query = query,
            onQueryChange = { query = it },
            twinQuery = twinQuery,
            onTwinQueryChange = { twinQuery = it },
            showLeadingIcon = showLeadingIcon,
            showClearButton = showClearButton,
            boxBackground = boxBackground,
        )
        HorizontalDivider()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "AiGlow Playground", style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = {
                        componentIndex = 0; showLeadingIcon = true; showClearButton = true
                        boxBackground = true; showTwin = false; query = ""; twinQuery = ""
                        cornerRadius = 28f
                        borderGlow = true; ringPaletteIndex = 0; strokeWidth = 2f
                        customColors.clear(); customColors.addAll(listOf(Color(0xFF2979FF), Color(0xFFD500F9)))
                        blurRadius = 16f; haloSameColors = true; haloPaletteIndex = 1
                        haloAutoWidth = true; haloWidth = 4f; haloDirectionIndex = 0
                        backgroundGlow = false; bgPaletteIndex = 1; bgAlpha = 0.35f
                        bgBloom = 20f; bgDuration = 6_000f
                        rotationDuration = 4_000f; animated = true; easingIndex = 0
                        alpha = 1f; enabled = true; stateAware = true
                    },
                ) { Text("Reset") }
            }

            SectionTitle("Component")
            ChoiceChips(
                options = listOf("Search Bar", "FAB", "Box"),
                selectedIndex = componentIndex,
                onSelect = { componentIndex = it },
            )
            when (componentIndex) {
                0 -> {
                    LabeledSwitch("Leading search icon", showLeadingIcon) { showLeadingIcon = it }
                    LabeledSwitch("Clear button", showClearButton) { showClearButton = it }
                }
                2 -> LabeledSwitch("Opaque background", boxBackground) { boxBackground = it }
            }
            LabeledSwitch("Twin preview — independence check (0.5× duration)", showTwin) { showTwin = it }

            SectionTitle("Shape")
            LabeledSlider(
                label = "Corner radius (0 = rectangle, 28+ = capsule)",
                valueText = "${cornerRadius.roundToInt()}dp",
                value = cornerRadius,
                valueRange = 0f..48f,
                onValueChange = { cornerRadius = it },
            )

            SectionTitle("Border glow (ring)")
            LabeledSwitch("Enable border glow", borderGlow) { borderGlow = it }
            if (borderGlow) {
                ChoiceChips(
                    options = PresetPalettes.map { it.first } + "Custom",
                    selectedIndex = ringPaletteIndex,
                    onSelect = { ringPaletteIndex = it },
                )
                if (ringPaletteIndex == CustomPaletteIndex) {
                    Text(
                        text = "Tap swatches to build the gradient — numbers show the color order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ColorSwatchGrid(
                        selected = customColors,
                        onToggle = { color ->
                            if (!customColors.remove(color)) customColors.add(color)
                        },
                    )
                }
                LabeledSlider(
                    label = "Stroke width",
                    valueText = "${strokeWidth.fmt1()}dp",
                    value = strokeWidth,
                    valueRange = 0.5f..10f,
                    onValueChange = { strokeWidth = it },
                )
                LabeledSlider(
                    label = "Halo blur radius (0 = no halo)",
                    valueText = "${blurRadius.roundToInt()}dp",
                    value = blurRadius,
                    valueRange = 0f..40f,
                    onValueChange = { blurRadius = it },
                )
                LabeledSwitch("Halo colors = ring colors", haloSameColors) { haloSameColors = it }
                if (!haloSameColors) {
                    ChoiceChips(
                        options = PresetPalettes.map { it.first },
                        selectedIndex = haloPaletteIndex,
                        onSelect = { haloPaletteIndex = it },
                    )
                }
                LabeledSwitch("Halo width auto (stroke × 2)", haloAutoWidth) { haloAutoWidth = it }
                LabeledSlider(
                    label = "Halo stroke width",
                    valueText = if (haloAutoWidth) "auto" else "${haloWidth.fmt1()}dp",
                    value = haloWidth,
                    valueRange = 1f..24f,
                    onValueChange = { haloWidth = it },
                    enabled = !haloAutoWidth,
                )
                Text(
                    text = "Halo direction (Inward = inner glow)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ChoiceChips(
                    options = HaloDirection.entries.map { it.name },
                    selectedIndex = haloDirectionIndex,
                    onSelect = { haloDirectionIndex = it },
                )
            }

            SectionTitle("Background glow (surface)")
            LabeledSwitch("Enable background glow", backgroundGlow) { backgroundGlow = it }
            if (backgroundGlow) {
                ChoiceChips(
                    options = PresetPalettes.map { it.first },
                    selectedIndex = bgPaletteIndex,
                    onSelect = { bgPaletteIndex = it },
                )
                LabeledSlider(
                    label = "Surface opacity",
                    valueText = bgAlpha.fmt2(),
                    value = bgAlpha,
                    valueRange = 0f..1f,
                    onValueChange = { bgAlpha = it },
                )
                LabeledSlider(
                    label = "Bloom radius (0 = crisp fill)",
                    valueText = "${bgBloom.roundToInt()}dp",
                    value = bgBloom,
                    valueRange = 0f..40f,
                    onValueChange = { bgBloom = it },
                )
                LabeledSlider(
                    label = "Rotation duration (independent of ring)",
                    valueText = "${bgDuration.roundToInt()}ms",
                    value = bgDuration,
                    valueRange = 500f..12_000f,
                    onValueChange = { bgDuration = it },
                )
            }

            SectionTitle("Animation")
            LabeledSlider(
                label = "Ring rotation duration",
                valueText = "${rotationDuration.roundToInt()}ms",
                value = rotationDuration,
                valueRange = 500f..10_000f,
                onValueChange = { rotationDuration = it },
            )
            LabeledSwitch("Animated (off = static gradient)", animated) { animated = it }
            ChoiceChips(
                options = EasingOptions.map { it.first },
                selectedIndex = easingIndex,
                onSelect = { easingIndex = it },
            )

            SectionTitle("State")
            LabeledSlider(
                label = "Ring alpha (overall glow opacity)",
                valueText = alpha.fmt2(),
                value = alpha,
                valueRange = 0f..1f,
                onValueChange = { alpha = it },
            )
            LabeledSwitch("Enabled (Search Bar / Box; M3 FAB has no disabled)", enabled) { enabled = it }
            LabeledSwitch("State-aware style (dim idle, bright on focus/press)", stateAware) { stateAware = it }

            SectionTitle("Generated code")
            ConfigCodeBlock(
                code = buildConfigCode(
                    borderGlow = borderGlow,
                    ringColors = ringColors,
                    strokeWidth = strokeWidth,
                    blurRadius = blurRadius,
                    rotationDuration = rotationDuration.roundToInt(),
                    cornerRadius = cornerRadius.roundToInt(),
                    haloSameColors = haloSameColors,
                    haloColors = if (haloSameColors) null else PresetPalettes[haloPaletteIndex].second,
                    haloAutoWidth = haloAutoWidth,
                    haloWidth = haloWidth,
                    haloDirection = HaloDirection.entries[haloDirectionIndex],
                    alpha = alpha,
                    animated = animated,
                    easingName = EasingOptions[easingIndex].first,
                    stateAware = stateAware,
                    backgroundGlow = backgroundGlow,
                    bgColors = PresetPalettes[bgPaletteIndex].second,
                    bgAlpha = bgAlpha,
                    bgBloom = bgBloom,
                    bgDuration = bgDuration.roundToInt(),
                ),
            )
        }
    }
}

/**
 * Pinned preview so every tweak is visible while scrolling the controls.
 * (한국어) 컨트롤을 스크롤해도 항상 보이도록 상단에 고정된 프리뷰.
 */
@Composable
private fun PreviewPane(
    componentIndex: Int,
    ringStyle: AiGlowStyle?,
    bgStyle: AiGlowStyle?,
    twinRingStyle: AiGlowStyle?,
    twinBgStyle: AiGlowStyle?,
    showTwinInstance: Boolean,
    enabled: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    twinQuery: String,
    onTwinQueryChange: (String) -> Unit,
    showLeadingIcon: Boolean,
    showClearButton: Boolean,
    boxBackground: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (componentIndex) {
            0 -> {
                PlaygroundSearchBar(query, onQueryChange, enabled, showLeadingIcon, showClearButton, ringStyle, bgStyle)
                if (showTwinInstance) {
                    PlaygroundSearchBar(twinQuery, onTwinQueryChange, enabled, showLeadingIcon, showClearButton, twinRingStyle, twinBgStyle)
                }
            }
            1 -> Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                AiGlowFloatingActionButton(onClick = {}, glowStyle = ringStyle, backgroundGlowStyle = bgStyle) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
                if (showTwinInstance) {
                    AiGlowFloatingActionButton(onClick = {}, glowStyle = twinRingStyle, backgroundGlowStyle = twinBgStyle) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }
            else -> {
                PlaygroundBox(ringStyle, bgStyle, enabled, boxBackground)
                if (showTwinInstance) PlaygroundBox(twinRingStyle, twinBgStyle, enabled, boxBackground)
            }
        }
    }
}

@Composable
private fun PlaygroundSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
    showLeadingIcon: Boolean,
    showClearButton: Boolean,
    ringStyle: AiGlowStyle?,
    bgStyle: AiGlowStyle?,
) {
    AiGlowSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = { Text("Search anything…") },
        leadingIcon = if (showLeadingIcon) {
            { Icon(Icons.Default.Search, contentDescription = null) }
        } else {
            null
        },
        trailingIcon = if (showClearButton && query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear query")
                }
            }
        } else {
            null
        },
        onSearch = { /* run search */ },
        glowStyle = ringStyle,
        backgroundGlowStyle = bgStyle,
    )
}

@Composable
private fun PlaygroundBox(
    ringStyle: AiGlowStyle?,
    bgStyle: AiGlowStyle?,
    enabled: Boolean,
    background: Boolean,
) {
    AiGlowBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        glowStyle = ringStyle,
        backgroundGlowStyle = bgStyle,
        enabled = enabled,
        onClick = { /* action */ },
        backgroundColor = if (background) MaterialTheme.colorScheme.surfaceVariant else Color.Unspecified,
        contentAlignment = Alignment.Center,
    ) {
        Text("AiGlowBox — press me")
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChoiceChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, enabled = enabled)
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Ordered multi-select swatches: the number badge shows each color's position in the
 * gradient. (한국어) 순서 있는 다중 선택 견본 — 숫자가 그라디언트 내 순서입니다.
 */
@Composable
private fun ColorSwatchGrid(selected: List<Color>, onToggle: (Color) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SwatchColors.forEach { color ->
            val orderIndex = selected.indexOf(color)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (orderIndex >= 0) 3.dp else 1.dp,
                        color = if (orderIndex >= 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onToggle(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (orderIndex >= 0) {
                    Text(
                        text = "${orderIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Renders the current selection as copy-pastable Kotlin. (한국어) 현재 설정을
 * 복사해서 바로 쓸 수 있는 Kotlin 코드로 출력합니다.
 */
private fun buildConfigCode(
    borderGlow: Boolean,
    ringColors: List<Color>,
    strokeWidth: Float,
    blurRadius: Float,
    rotationDuration: Int,
    cornerRadius: Int,
    haloSameColors: Boolean,
    haloColors: List<Color>?,
    haloAutoWidth: Boolean,
    haloWidth: Float,
    haloDirection: HaloDirection,
    alpha: Float,
    animated: Boolean,
    easingName: String,
    stateAware: Boolean,
    backgroundGlow: Boolean,
    bgColors: List<Color>,
    bgAlpha: Float,
    bgBloom: Float,
    bgDuration: Int,
): String = buildString {
    fun styleExpr(configName: String): String =
        if (stateAware) "AiGlowDefaults.interactiveStyle($configName)" else "AiGlowStyle(idle = $configName)"

    if (borderGlow) {
        appendLine("val ringConfig = GlowConfig(")
        appendLine("    colors = listOf(${ringColors.joinToString { it.toHexLiteral() }}),")
        appendLine("    strokeWidth = ${strokeWidth.fmt1()}.dp,")
        appendLine("    blurRadius = ${blurRadius.roundToInt()}.dp,")
        appendLine("    rotationDuration = $rotationDuration,")
        appendLine("    shape = RoundedCornerShape($cornerRadius.dp),")
        if (!haloSameColors && haloColors != null) {
            appendLine("    haloColors = listOf(${haloColors.joinToString { it.toHexLiteral() }}),")
        }
        if (!haloAutoWidth) {
            appendLine("    haloStrokeWidth = ${haloWidth.fmt1()}.dp,")
        }
        if (haloDirection != HaloDirection.Outward) {
            appendLine("    haloDirection = HaloDirection.${haloDirection.name},")
        }
        appendLine("    alpha = ${alpha.fmt2()}f,")
        appendLine("    animated = $animated,")
        appendLine("    easing = ${easingName}Easing,")
        appendLine(")")
    }
    if (backgroundGlow) {
        appendLine("val backgroundConfig = GlowConfig(")
        appendLine("    colors = listOf(${bgColors.joinToString { it.toHexLiteral() }}),")
        appendLine("    blurRadius = ${bgBloom.roundToInt()}.dp, // bloom")
        appendLine("    rotationDuration = $bgDuration,")
        appendLine("    shape = RoundedCornerShape($cornerRadius.dp),")
        appendLine("    alpha = ${bgAlpha.fmt2()}f,")
        appendLine("    animated = $animated,")
        appendLine("    easing = ${easingName}Easing,")
        appendLine(")")
    }
    appendLine("val glowStyle = ${if (borderGlow) styleExpr("ringConfig") else "null"}")
    append("val backgroundGlowStyle = ${if (backgroundGlow) styleExpr("backgroundConfig") else "null"}")
}

@Composable
private fun ConfigCodeBlock(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        SelectionContainer {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private fun Color.toHexLiteral(): String = "Color(0x${"%08X".format(toArgb())})"

private fun Float.fmt1(): String = ((this * 10).roundToInt() / 10f).toString()

private fun Float.fmt2(): String = ((this * 100).roundToInt() / 100f).toString()
