# Contributing

**English** | [한국어](CONTRIBUTING.ko.md)

Project architecture and conventions (including for AI coding agents) live in [AGENTS.md](AGENTS.md) — read that first.

## Workflow

`main` is protected: no direct pushes, PRs required, CI must pass. There is no `develop`/`release` branch — `main` is always releasable and tags (`v1.0.0`, ...) are cut directly from it (see [PUBLISHING.md](PUBLISHING.md)).

1. Branch off `main`: `type/short-description`, e.g. `feat/halo-easing`, `fix/blur-fallback-api26`.
2. Open a PR into `main`. CI (build + unit tests + lint on `:aiglow`) must pass.
3. PRs are **squash-merged** — all commits in your branch collapse into one commit on `main`, titled after the PR title. Commit freely on your branch; only the **PR title** needs to follow the convention below.
4. No mandatory second-reviewer approval (single-maintainer project today) — self-merge once CI is green.

## Commit / PR title convention

[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): subject`

- **Types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`
- **Scope** (optional): the affected area, e.g. `aiglow`, `searchbar`, `fab`, `box`, `readme`
- Example: `feat(aiglow): add haloColors override to GlowConfig`

## Before opening a PR

```bash
./gradlew :aiglow:assembleRelease :aiglow:testDebugUnitTest :aiglow:lintDebug
```

Follow the architecture invariants in [AGENTS.md](AGENTS.md) — in particular: no mutable global/companion state, `@Immutable` + `copy()`-based configs (no `var`/Builder), animated values read only in the draw phase.
