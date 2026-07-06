<!--
PR title must follow Conventional Commits — it becomes the squash commit
message on main: type(scope): subject, e.g. "feat(aiglow): add haloColors override"
Types: feat | fix | docs | refactor | test | chore | build | ci | perf
(한국어) PR 제목은 Conventional Commits 형식을 따라야 합니다 — squash 머지 시
그대로 main의 커밋 메시지가 됩니다.
-->

## What & why

<!-- What changed, and why. Link an issue if one exists. -->

## Checklist

- [ ] `./gradlew :aiglow:assembleRelease :aiglow:testDebugUnitTest :aiglow:lintDebug` passes locally
- [ ] Public API changes: `GlowConfig`/`AiGlowStyle` stay `@Immutable` with `val`-only, `copy()`-based customization
- [ ] KDoc added/updated explaining **why**, English + `(한국어)` — see [AGENTS.md](../AGENTS.md)
- [ ] `README.md` **and** `README.ko.md` updated together, if user-facing behavior changed
