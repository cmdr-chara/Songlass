import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.cmdrchara.songlass.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }

        // Runs commonTest on the local JVM, so tests work on any host (iOS tests need macOS).
        withHostTest {}
    }

    sourceSets {
        androidMain.dependencies {
            // Ktor lives only in the Android source set: commonMain keeps the plain HttpTransport
            // interface, and iOS has no tested engine yet.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            // Public JSON helpers return kotlinx.serialization JsonElement trees; features #9/#12
            // consume the same API, so it is an `api` dependency.
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
