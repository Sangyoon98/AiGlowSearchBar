plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "com.sangyoon.aiglow"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Types exposed in the public API surface (Modifier, Color, Dp, Shape,
    // InteractionSource, TextFieldColors, ...) must be visible to consumers at
    // compile time, so they are declared as `api`.
    // (한국어) 공개 API 시그니처에 노출되는 타입은 소비자 모듈이 컴파일 시점에
    // 볼 수 있어야 하므로 api로 공개한다. TextFieldColors 등 M3 타입도
    // AiGlowSearchBar 파라미터로 노출되므로 material3 역시 api다.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Publishing configuration for JitPack, Maven Central and other repositories.
// (한국어) JitPack, Maven Central 등의 저장소에 배포하기 위한 설정.
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.sangyoon"
                artifactId = "aiglow"
                version = "1.0.0" // Replace with actual version
            }
        }
    }
}
