package com.sangyoon.aiglow

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction

/**
 * A Material text-field-based search bar wrapped in a rotating AI glow.
 *
 * Why it is built on Material's [OutlinedTextField]: the glow needs a shaped,
 * opaque container with transparent stock borders — exactly what
 * [AiGlowDefaults.searchBarColors] configures — while inheriting all Material
 * behaviors (cursor, selection, IME, a11y) for free instead of re-implementing them.
 *
 * Why state hoisting (query/onQueryChange): the component never owns the query, so
 * callers keep it in a ViewModel or anywhere else, and this function stays stateless
 * and trivially testable (official state-hoisting guidance).
 *
 * Two independent glow layers are available: [glowStyle] draws the edge ring
 * (halo + crisp gradient border) and [backgroundGlowStyle] fills the surface itself
 * from the flowing perimeter colors toward one mixed center color, then blooms
 * outward. Use either alone or both together — each has its own
 * colors/opacity/rotation speed. When a background glow is set, the default [colors]
 * switch to transparent containers so the fill is visible through the text field.
 * Both styles are forced onto the same resolved container `shape`
 * before drawing (see [AiGlowStyle.pinnedToShape]), so per-state or ring-vs-background
 * shape differences can never make the glow diverge from the container outline.
 *
 * The glow reacts to focus/press through the shared [interactionSource]:
 * `glowStyle.focused` lights up while typing. The shape from `glowStyle.idle`
 * (or `backgroundGlowStyle.idle` when only that is set) is applied to the text field
 * too, so the glow and the container outline always match.
 *
 * Why [backgroundGlowStyle] is the last parameter (after [interactionSource]) instead
 * of sitting next to [glowStyle]: this keeps every 1.0.0 parameter at its original
 * position, so existing positional call sites keep compiling against 1.1.0+. Because
 * [colors]' smart default needs to see whether a background glow is set — and Kotlin
 * forbids a default expression from referencing a parameter declared later — [colors]
 * itself defaults to `null` and the actual default is resolved in the function body.
 *
 * (한국어) Material OutlinedTextField 기반의 글로우 검색 바입니다. 커서/IME/접근성 등
 * Material 동작을 그대로 상속하고, 상태 호이스팅으로 stateless를 유지합니다.
 * 글로우 레이어는 두 가지가 독립적으로 제공됩니다: [glowStyle]은 테두리 링,
 * [backgroundGlowStyle]은 흐르는 둘레 색을 표면 안쪽의 하나의 혼합 중심 색으로 모은 뒤
 * 바깥으로도 번지는 배경 글로우입니다. 각각 단독으로도, 함께도 쓸 수 있고
 * 색/투명도/회전 속도를 따로 가집니다. 배경 글로우 지정 시 기본 [colors]가 투명
 * 컨테이너로 전환되어 채움이 비쳐 보입니다. 두 스타일 모두 그리기
 * 전에 동일한 컨테이너 shape으로 강제 고정되어([AiGlowStyle.pinnedToShape]) 상태별/
 * 링-배경 간 shape 차이로 글로우가 컨테이너 외곽선과 어긋나는 일이 없습니다.
 * 같은 InteractionSource를 공유해 포커스/눌림에 글로우가 반응합니다.
 *
 * [backgroundGlowStyle]을 [interactionSource] 뒤 맨 끝에 둔 이유: 1.0.0의 모든
 * 파라미터를 원래 위치 그대로 유지해 기존 positional 호출이 1.1.0+에서도 계속
 * 컴파일되게 하기 위함입니다. [colors]의 스마트 기본값이 배경 글로우 지정 여부를
 * 참조해야 하는데, Kotlin은 더 뒤에 선언된 파라미터를 기본값 식에서 참조할 수 없게
 * 하므로 [colors] 자체는 null을 기본값으로 두고 실제 기본값은 함수 본문에서 계산합니다.
 *
 * @param query Current query text, owned by the caller. (한국어) 호출자가 소유하는 검색어.
 * @param onQueryChange Called on every text change. (한국어) 검색어 변경 콜백.
 * @param modifier Layout modifier. (한국어) 배치용 Modifier.
 * @param enabled `false` disables input and dims the glow (via [AiGlowStyle.disabled]).
 *   (한국어) false면 입력이 막히고 글로우도 비활성 상태로 전환됩니다.
 * @param readOnly Text cannot be modified but can be selected/copied.
 *   (한국어) 수정 불가, 선택/복사는 가능.
 * @param placeholder Shown while [query] is empty. (한국어) 검색어가 없을 때 표시.
 * @param leadingIcon Slot at the start — any composable (icon, avatar, ...).
 *   (한국어) 좌측 슬롯 — 아이콘 등 임의 컴포저블.
 * @param trailingIcon Slot at the end — e.g. a clear button. (한국어) 우측 슬롯.
 * @param onSearch Invoked when the IME search action fires. (한국어) IME 검색 액션 콜백.
 * @param keyboardOptions Defaults to an IME Search action. (한국어) 기본 IME 액션은 Search.
 * @param textStyle Style of the input text. (한국어) 입력 텍스트 스타일.
 * @param glowStyle Per-interaction-state edge-ring glow; `null` draws no ring.
 *   (한국어) 상태별 테두리 글로우. null이면 링을 그리지 않습니다.
 * @param colors Material text field colors. `null` (default) picks opaque containers
 *   normally, or transparent containers when [backgroundGlowStyle] is set.
 *   (한국어) null(기본)이면 글로우 모드에 따라 불투명/투명 컨테이너가 자동 선택됩니다.
 * @param interactionSource Pass your own to observe/share interactions; `null` creates
 *   an internal one. (한국어) 직접 관찰하려면 전달, null이면 내부 생성.
 * @param backgroundGlowStyle Per-interaction-state surface glow; `null` (default)
 *   draws no fill. Its resolved shape should be single-contour and convex.
 *   (한국어) 상태별 배경(표면) 글로우. null(기본)이면 채움이 없으며, 적용할 shape는 단일
 *   외곽선의 볼록한 형태여야 합니다.
 */
@Composable
fun AiGlowSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    textStyle: TextStyle = LocalTextStyle.current,
    glowStyle: AiGlowStyle? = AiGlowDefaults.interactiveStyle(),
    colors: TextFieldColors? = null,
    interactionSource: MutableInteractionSource? = null,
    backgroundGlowStyle: AiGlowStyle? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shape = (glowStyle ?: backgroundGlowStyle)?.idle?.shape ?: GlowConfig().shape
    val resolvedColors = colors
        ?: if (backgroundGlowStyle != null) AiGlowDefaults.glassSearchBarColors() else AiGlowDefaults.searchBarColors()

    // Ring first (outer), fill second (inner): the outer modifier wraps the inner
    // one's drawing, so the layering is halo → fill → content → crisp ring.
    // (한국어) 링을 바깥, 채움을 안쪽에 체이닝 — halo → 채움 → 콘텐츠 → 링 순서가 된다.
    var glowModifier = modifier
    if (glowStyle != null) {
        glowModifier = glowModifier.aiGlow(glowStyle.pinnedToShape(shape), source, enabled)
    }
    if (backgroundGlowStyle != null) {
        glowModifier = glowModifier.aiGlowBackground(backgroundGlowStyle.pinnedToShape(shape), source, enabled)
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = glowModifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(query) }),
        singleLine = true,
        interactionSource = source,
        shape = shape,
        colors = resolvedColors,
    )
}
