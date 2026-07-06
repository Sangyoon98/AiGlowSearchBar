# Publishing AiGlow

## How to release a new version

### 1. Update version in code
Edit `aiglow/build.gradle.kts`, update the `version` field in the `publishing` block:

```kotlin
version = "1.0.1" // <- change this
```

Also update `README.md` + `README.ko.md` if referring to a specific version.

### 2. Sanity-check the release build locally
```bash
./gradlew :aiglow:assembleRelease :aiglow:testDebugUnitTest
```

### 3. Create a git tag and push
```bash
git tag v1.0.1
git push origin main
git push origin v1.0.1
```

### 4. (Optional) GitHub: create a Release

Go to https://github.com/Sangyoon98/AiGlowSearchBar/releases/new, select the tag, describe the changes, and publish. This makes the release discoverable.

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
