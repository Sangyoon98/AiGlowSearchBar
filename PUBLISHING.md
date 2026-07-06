# Publishing AiGlow

## How to release a new version

### 1. Update version in code
Edit `aiglow/build.gradle.kts`, update the `version` field in the `publishing` block:

```kotlin
version = "1.0.1" // <- change this
```

Also update `README.md` + `README.ko.md` if referring to a specific version.

### 2. Create a git tag and push
```bash
git tag v1.0.1
git push origin main
git push origin v1.0.1
```

### 3. (Optional) GitHub: create a Release

Go to https://github.com/YOUR_USERNAME/aiglow/releases/new, select the tag, describe the changes, and publish. This makes the release discoverable.

### 4. JitPack will build automatically

JitPack monitors your GitHub tags and builds them on-demand. Consumers add:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.YOUR_USERNAME:aiglow:v1.0.1'
}
```

**First-time build takes 1–2 minutes on JitPack.** After that, it's cached.

## Alternative: Maven Central (Sonatype OSSRH)

For broader distribution, register with [Sonatype OSSRH](https://central.sonatype.org/):

1. Create an account at https://issues.sonatype.org
2. Request namespace approval for `com.sangyoon`
3. Add GPG signing and Sonatype credentials to `~/.gradle/gradle.properties`
4. Use the `gradle-nexus/publish-plugin` or manual `./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository`

This is more involved but places your library on Maven Central, used by default in Android projects.

---

**Recommended:** start with JitPack (instant, no setup), then move to Maven Central once the library is stable.
