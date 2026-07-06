# 기여 가이드

[English](CONTRIBUTING.md) | **한국어**

프로젝트 아키텍처와 컨벤션(AI 코딩 에이전트용 포함)은 [AGENTS.md](AGENTS.md)에 정리되어 있습니다 — 먼저 읽어주세요.

## 작업 흐름

`main`은 보호 브랜치입니다: 직접 push 불가, PR 필수, CI 통과 필수. `develop`/`release` 브랜치는 따로 두지 않습니다 — `main`은 항상 릴리스 가능한 상태를 유지하고, 태그(`v1.0.0` 등)는 `main`에서 바로 잘라냅니다([PUBLISHING.md](PUBLISHING.md) 참고).

1. `main`에서 브랜치 생성: `type/짧은-설명`, 예) `feat/halo-easing`, `fix/blur-fallback-api26`
2. `main`으로 PR 생성. CI(`:aiglow` 빌드+유닛테스트+린트)가 통과해야 합니다.
3. PR은 **squash 머지**됩니다 — 브랜치 안의 모든 커밋이 PR 제목을 커밋 메시지로 하는 커밋 1개로 합쳐집니다. 브랜치 안에서는 커밋을 자유롭게 하셔도 되고, **PR 제목**만 아래 컨벤션을 따르면 됩니다.
4. 별도 승인자 필수 조건은 없습니다(현재 단일 관리자 프로젝트) — CI가 통과하면 셀프 머지 가능합니다.

## 커밋/PR 제목 컨벤션

[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): subject`

- **타입:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`
- **스코프**(선택): 영향받는 영역, 예) `aiglow`, `searchbar`, `fab`, `box`, `readme`
- 예시: `feat(aiglow): add haloColors override to GlowConfig`

## PR을 열기 전에

```bash
./gradlew :aiglow:assembleRelease :aiglow:testDebugUnitTest :aiglow:lintDebug
```

[AGENTS.md](AGENTS.md)의 아키텍처 불변식을 지켜주세요 — 특히: 전역/companion mutable 상태 금지, `@Immutable` + `copy()` 기반 설정(var/Builder 금지), 애니메이션 값은 draw phase에서만 읽기.
