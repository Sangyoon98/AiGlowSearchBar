# Publishing AiGlow

## How to release a new version

### 1. Bump the version (via PR)

`main` is protected — no direct pushes. Branch, bump, PR, merge (see [CONTRIBUTING.md](CONTRIBUTING.md)):

```bash
git checkout -b chore/release-v1.0.1
```

Edit `aiglow/build.gradle.kts`, update the `version` field in the `publishing` block:

```kotlin
version = "1.0.1" // <- change this
```

Also update `README.md` + `README.ko.md` if referring to a specific version. Open a PR titled e.g. `chore(release): bump version to 1.0.1`, wait for CI, squash-merge it.

### 2. Sanity-check the release build locally

```bash
./gradlew :aiglow:assembleRelease :aiglow:testDebugUnitTest
```

(Also runs automatically as part of CI on the PR above.)

### 3. Tag the merged commit and push

Tags are not covered by the `main` branch ruleset, so pushing a tag is a direct operation — no PR needed for this step:

```bash
git checkout main && git pull
git tag v1.0.1
git push origin v1.0.1
```

### 4. GitHub Release — automatic

Pushing a `v*` tag triggers [`.github/workflows/release.yml`](.github/workflows/release.yml), which builds the release AAR + sources jar, attaches them, and publishes a GitHub Release with auto-generated notes (from PRs merged since the last tag — this is why squash-merged PR titles should follow [Conventional Commits](CONTRIBUTING.md)). Nothing to do manually here.

### 5. Trigger the JitPack build

JitPack builds tags lazily, on first request. Trigger it by opening:

```
https://jitpack.io/#Sangyoon98/AiGlowSearchBar/v1.0.1
```

Wait for the green "Get it" status (first build takes 1–5 minutes), then consumers can add:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    // Multi-module coordinate: com.github.<user>.<repo>:<module>:<tag>
    implementation 'com.github.Sangyoon98.AiGlowSearchBar:aiglow:v1.0.1'
}
```

**Why the dotted `user.repo` groupId:** this repo has multiple Gradle modules (`:app`, `:aiglow`). JitPack's single-module shorthand `com.github.user:repo:tag` only works for single-module repos; multi-module repos must address the specific module with `com.github.user.repo:module:tag`. Double-check the exact coordinate on the JitPack page above — it lists every resolvable module for the build.

## Alternative: Maven Central (Sonatype OSSRH)

For broader distribution, register with [Sonatype OSSRH](https://central.sonatype.org/):

1. Create an account at https://issues.sonatype.org
2. Request namespace approval for `com.sangyoon`
3. Add GPG signing and Sonatype credentials to `~/.gradle/gradle.properties`
4. Use the `gradle-nexus/publish-plugin` or manual `./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository`

This is more involved but places your library on Maven Central, used by default in Android projects.

---

**Recommended:** start with JitPack (instant, no setup), then move to Maven Central once the library is stable.
