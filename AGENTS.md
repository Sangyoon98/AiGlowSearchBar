# AGENTS.md — AI agent guide for AiGlow

Context for AI coding agents (Claude Code, Codex, Cursor, ...). Humans: start with [README.md](README.md).

## What this is

Pure Jetpack Compose library (`:aiglow`) that draws a rotating "AI glow" — a blurred halo behind content plus a crisp sweep-gradient ring on its edge — around Material components, plus a playground sample app (`:app`). Open-source library: public API stability matters.

## Module map

- `:aiglow` — the library. **No Activity/Application Context dependencies.**
  - `GlowConfig.kt` — `@Immutable` visual config; halo fallback resolution (`resolvedHalo*`)
  - `AiGlowStyle.kt` — per-interaction-state configs + `resolve()` (priority: disabled > pressed > focused > hovered > idle)
  - `AiGlowDefaults.kt` — palettes, `interactiveStyle()`, `searchBarColors()` (immutable object, Material `*Defaults` convention)
  - `AiGlow.kt` — engine: `Modifier.aiGlow` overloads, `rememberGlowAngle`, `glowDraw` (drawWithCache renderer)
  - `AiGlowSearchBar.kt` / `AiGlowFloatingActionButton.kt` / `AiGlowBox.kt` — thin Material wrappers
- `:app` — playground demo (`GlowPlaygroundScreen.kt`). Not published.

## Architecture invariants — do not break

1. **No mutable global/companion/singleton state.** Animation state lives per call site via `rememberInfiniteTransition`; that is what guarantees multi-instance independence.
2. Configs are `@Immutable` data classes with `val` only; customization is `copy()`-based. Never expose `var` fields or Builders.
3. Shapes are the standard Compose `Shape` interface — no custom shape enums.
4. Animated values (angle, alpha) are read **only in the draw phase** via provider lambdas → zero recomposition while animating. Do not move those reads into composition.
5. Expensive draw objects (Outline/Path, shaders, Paint) live in `drawWithCache`; returned Modifiers are `remember`ed keyed on config + State objects so draw caches survive recomposition (e.g. keystrokes).
6. Halo blur uses `BlurMaskFilter` (hardware-accelerated API 28+; layered-stroke fallback on 26–27). **Do not use `Modifier.blur()`** — it blurs host content and is ignored below API 31.
7. Gradient rotation = shader local matrix, never canvas rotation (canvas rotation would tilt the outline shape).
8. Library hygiene: don't break public signatures; new params need defaults; `api` vs `implementation` in `aiglow/build.gradle.kts` mirrors the public API surface — types in public signatures must be `api`.

## Conventions

- KDoc on every public/internal declaration must explain **why** (design rationale), English first, then a `(한국어)` paragraph.
- User docs are bilingual: any change to `README.md` must be mirrored in `README.ko.md` (same for `CONTRIBUTING.md`/`.ko.md`).
- Code comments: English primary, Korean `(한국어)` alongside where helpful.

## Git workflow — `main` is protected

- **Never commit directly to `main`.** Branch as `type/short-description` (e.g. `feat/halo-easing`, `fix/blur-fallback-api26`) and open a PR. `main` has no direct-push access, even for the repo owner.
- PRs are **squash-merged**; the PR title becomes the commit on `main` and must follow Conventional Commits: `type(scope): subject` (types: `feat|fix|docs|refactor|test|chore|build|ci|perf`). Commits within the branch can be freeform.
- CI (`.github/workflows/ci.yml`: assemble + unit tests + lint on `:aiglow`) is a required status check — a PR cannot merge until it's green.
- Full detail: [CONTRIBUTING.md](CONTRIBUTING.md).

## Build & verify loop

- Toolchain: AGP 9.2.1 with **built-in Kotlin** (do NOT add `org.jetbrains.kotlin.android`), Kotlin 2.2, Compose BOM 2026.02, minSdk 26, compileSdk 36.1, JDK 17+.
- Compile: `./gradlew :app:assembleDebug`
- Unit tests (pure JVM value logic): `./gradlew :aiglow:testDebugUnitTest`
- Lint: `./gradlew :aiglow:lintDebug`
- Visual verification needs a device/emulator; the maintainer runs the playground manually — do not claim visual results you did not observe. The playground's "Twin preview" toggle is the multi-instance independence check.

## Pitfalls

- `Modifier.aiGlow` is a `@Composable` modifier factory — it cannot be called outside composition.
- State changes in `AiGlowStyle` animate **alpha only**; structural changes (colors/thickness/shape) intentionally swap at state boundaries with a one-time cache rebuild. Keep it that way unless redesigning cache keys.
- M3 `FloatingActionButton` has no `enabled` param; `AiGlowFloatingActionButton` follows that convention.
- Clipping parents (e.g. `verticalScroll`) cut the halo at their edges — keep padding around glowing components in demos.
- App icon is adaptive-only (`mipmap-anydpi` + vector drawables); safe since minSdk 26.
