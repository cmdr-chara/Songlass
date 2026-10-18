import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.cmdrchara.songlass.theme.materialglass"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Material Glass reuses the glass components and swaps their look (GlassLook).
            api(projects.themeLiquidglass)
            implementation(libs.haze)
            implementation(libs.haze.glass)
            implementation(libs.compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "dev.cmdrchara.songlass.theme.materialglass.resources"
}
