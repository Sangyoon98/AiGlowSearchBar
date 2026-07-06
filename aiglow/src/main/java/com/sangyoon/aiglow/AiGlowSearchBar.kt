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
 * The glow reacts to focus/press through the shared [interactionSource]:
 * `glowStyle.focused` lights up while typing. `glowStyle.idle.shape` is applied to
 * the text field too, so the glow and the container outline always match.
 *
 * (한국어) Material OutlinedTextField 기반의 글로우 검색 바입니다. 커서/IME/접근성 등
 * Material 동작을 그대로 상속하고, 상태 호이스팅으로 stateless를 유지합니다.
 * 같은 InteractionSource를 공유해 포커스/눌림에 글로우가 반응하며,
 * glowStyle.idle.shape가 텍스트필드 shape에도 적용되어 외곽선이 항상 일치합니다.
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
 * @param glowStyle Per-interaction-state glow. (한국어) 상태별 글로우 스타일.
 * @param colors Material text field colors; the default hides stock borders so the
 *   gradient ring is the only border. (한국어) 기본값은 기본 테두리를 숨겨 링이 테두리가 됩니다.
 * @param interactionSource Pass your own to observe/share interactions; `null` creates
 *   an internal one. (한국어) 직접 관찰하려면 전달, null이면 내부 생성.
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
    glowStyle: AiGlowStyle = AiGlowDefaults.interactiveStyle(),
    colors: TextFieldColors = AiGlowDefaults.searchBarColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.aiGlow(glowStyle, source, enabled),
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
        shape = glowStyle.idle.shape,
        colors = colors,
    )
}
