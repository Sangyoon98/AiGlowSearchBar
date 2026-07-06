# AiGlow

[English](README.md) | **한국어**

UI를 회전하는 AI 스타일 그라디언트 글로우로 감싸는 순수 Jetpack Compose 라이브러리입니다. 콘텐츠 뒤에는 부드럽게 번지는 halo를, 가장자리에는 선명한 sweep-gradient 링을 그립니다.

- **전역 상태 제로** — 모든 글로우 컴포넌트는 구조적으로 독립 애니메이션됩니다.
- **애니메이션 중 recomposition 제로** — 회전 각도는 draw phase에서만 읽습니다.
- **지원하는 모든 API 레벨(26+)에서 동작** — 블러 halo 포함.
- **Activity / Application Context 의존 없음** — 어떤 Compose 모듈에서도 안전합니다.

## 컴포넌트

| 컴포넌트 | 설명 |
|---|---|
| `AiGlowSearchBar` | Material 3 `OutlinedTextField` 기반 검색 바. 포커스/눌림에 글로우가 반응하며 placeholder, 좌우 슬롯(아이콘 또는 임의 컴포저블), IME 검색 액션, 활성/읽기전용 상태를 지원합니다. |
| `AiGlowFloatingActionButton` | 누르는 동안 밝아지는 글로우를 두른 Material 3 FAB. |
| `AiGlowBox` | *어떤* 콘텐츠든 글로우로 감싸는 범용 컨테이너. 클릭(ripple 포함)도 선택적으로 지원합니다. |
| `Modifier.aiGlow(...)` | 위 컴포넌트들이 공유하는 원본 Modifier — 원하는 컴포저블에 직접 글로우를 붙일 수 있습니다. |

## 설치

라이브러리는 `:aiglow` Gradle 모듈에 있습니다.

```kotlin
// settings.gradle.kts
include(":aiglow")

// 사용하는 모듈의 build.gradle.kts
dependencies {
    implementation(project(":aiglow"))
}
```

> Maven Central / JitPack 좌표는 배포 후 제공될 예정입니다.

**요구 사항:** minSdk 26, Kotlin 2.2+, Jetpack Compose (BOM 2026.02+), Material 3.

## 빠른 시작

```kotlin
var query by rememberSaveable { mutableStateOf("") }

AiGlowSearchBar(
    query = query,
    onQueryChange = { query = it },
    modifier = Modifier.fillMaxWidth(),
    placeholder = { Text("무엇이든 검색…") },
    onSearch = { runSearch(it) },
)
```

FAB과 글로우 카드:

```kotlin
AiGlowFloatingActionButton(onClick = { /* … */ }) {
    Icon(Icons.Default.Add, contentDescription = "추가")
}

AiGlowBox(
    modifier = Modifier.size(200.dp, 96.dp),
    onClick = { /* … */ },
    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
    contentAlignment = Alignment.Center,
) {
    Text("아무 콘텐츠나")
}
```

원본 Modifier로 무엇이든 빛나게:

```kotlin
Card(modifier = Modifier.aiGlow(GlowConfig(shape = RoundedCornerShape(12.dp)))) { /* … */ }
```

## 커스터마이징

모든 것은 불변 `GlowConfig`가 결정합니다 — `copy()`로 커스터마이징하세요:

```kotlin
val config = GlowConfig(
    colors = AiGlowDefaults.AuroraColors,       // 링 그라디언트 색
    strokeWidth = 3.dp,                          // 링 두께
    blurRadius = 24.dp,                          // halo 번짐 (0.dp = halo 없음)
    rotationDuration = 1_500,                    // 한 바퀴(360°) 시간(ms)
    shape = RoundedCornerShape(20.dp),           // 0.dp = 사각형 … 28.dp+ = 캡슐
    haloColors = listOf(Color.Cyan, Color.Blue), // 별도 "셰도우" 팔레트 (선택)
    haloStrokeWidth = 8.dp,                      // halo 두께 (기본 strokeWidth * 2)
    alpha = 0.9f,                                // 글로우 전체 불투명도
    animated = true,                             // false = 정지된 그라디언트
    easing = LinearEasing,                       // 회전 easing
)
```

| 파라미터 | 타입 | 기본값 | 의미 |
|---|---|---|---|
| `colors` | `List<Color>` | `AiGlowDefaults.GeminiColors` | 링의 sweep 그라디언트 색 |
| `strokeWidth` | `Dp` | `2.dp` | 링 두께 |
| `blurRadius` | `Dp` | `16.dp` | halo 번짐 반경. `0.dp`면 halo 없음 |
| `rotationDuration` | `Int` (ms) | `4000` | 한 바퀴 도는 시간 |
| `shape` | `Shape` | `RoundedCornerShape(28.dp)` | 글로우와 호스트 컴포넌트가 **공유**하는 외곽선 — 임의 radius, 커스텀 `Shape` 모두 가능 |
| `haloColors` | `List<Color>?` | `null` (= `colors`) | halo/셰도우 전용 팔레트 |
| `haloStrokeWidth` | `Dp?` | `null` (= `strokeWidth * 2`) | halo 링 두께 |
| `alpha` | `Float` | `1f` | 글로우 전체 불투명도 |
| `animated` | `Boolean` | `true` | 회전 on/off |
| `easing` | `Easing` | `LinearEasing` | 회전 easing 곡선 |

기본 제공 팔레트: `AiGlowDefaults.GeminiColors`, `AuroraColors`, `SunsetColors`, `MintColors`.

## 인터랙션 상태

`AiGlowStyle`은 인터랙션 상태마다 `GlowConfig`를 하나씩 담습니다. 지정하지 않은 상태는 `idle`로 폴백하며, 우선순위는 `disabled > pressed > focused > hovered > idle`입니다.

```kotlin
// 기본 제공 스타일: idle은 은은하게, 포커스/눌림엔 환하게.
val style = AiGlowDefaults.interactiveStyle(GlowConfig(colors = AiGlowDefaults.SunsetColors))

// 또는 완전 수동 제어:
val custom = AiGlowStyle(
    idle = GlowConfig(alpha = 0.5f),
    focused = GlowConfig(alpha = 1f, rotationDuration = 2_000),
    pressed = GlowConfig(colors = AiGlowDefaults.MintColors),
    disabled = GlowConfig(alpha = 0.2f, animated = false), // 생략 시 → 흐려지고 정지된 idle 자동 파생
)

AiGlowSearchBar(query, onQueryChange, glowStyle = custom)
```

상태 간 alpha 차이는 부드럽게 애니메이션되고, 구조적 변화(색/두께/모양)는 상태 경계에서 전환됩니다.

## 동작 원리 (그리고 빠른 이유)

- **`GlowConfig`는 `@Immutable` data class** — 동일한 config가 전달되는 한 recomposition을 건너뛸 수 있게 하는 stability 계약입니다 ([Compose stability 가이드](https://developer.android.com/develop/ui/compose/performance/stability)).
- **회전 상태는 호출 지점별 composition에 격리** (`rememberInfiniteTransition`) — 글로우 컴포넌트를 몇 개를 배치해도 서로 간섭할 수 없습니다. 전역/`companion object` 상태가 어디에도 없습니다.
- **각도는 draw phase에서만 읽으므로** 애니메이션은 *그리기만* 무효화합니다: 60~120fps 내내 composition/layout 0회 ([Compose phases](https://developer.android.com/develop/ui/compose/phases), [읽기 지연](https://developer.android.com/develop/ui/compose/performance/bestpractices)).
- **비싼 객체(외곽선 Path, 셰이더, Paint)는 `drawWithCache`로 캐시**되어 크기/설정이 바뀔 때만 재생성됩니다 ([graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
- **그라디언트는 캔버스가 아닌 셰이더의 local matrix로 회전** — 모양은 기울지 않고 색만 흐릅니다.
- **halo는 `Modifier.blur()`가 아닌 `BlurMaskFilter` 사용** — `Modifier.blur()`는 콘텐츠까지 흐리게 하고 API 31 미만에서 무시됩니다. 마스크 필터는 글로우 스트로크만 흐리며 API 28부터 하드웨어 가속되고, API 26~27에서는 다층 스트로크 근사로 그려 `blurRadius`가 모든 기기에서 동작합니다.

## 데모 앱 — 인터랙티브 플레이그라운드

`:app` 모듈에는 모든 커스터마이징 옵션을 실시간으로 조작할 수 있는 플레이그라운드가 들어 있습니다:

- 컴포넌트 전환(Search Bar / FAB / Box)과 컴포넌트별 토글(아이콘, 지우기 버튼, 배경)
- 링 팔레트 프리셋 + 순서 있는 커스텀 팔레트 빌더(견본을 탭하면 숫자로 그라디언트 순서 표시)
- 링 두께, halo 두께, blur radius, corner radius(사각형 ↔ 캡슐), 회전 속도, alpha 슬라이더
- halo 색 분리, 애니메이션 on/off, easing 선택
- 활성/비활성, 상태 반응 스타일 토글
- 인스턴스 간 애니메이션 독립성을 확인하는 "트윈 프리뷰"(0.5× 주기)
- 현재 설정을 복사 가능한 Kotlin 코드로 보여주는 **Generated code** 블록

Android Studio에서 실행하세요.

## 라이선스

TBD.
