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
        namespace = "dev.cmdrchara.songlass.theme.miuix"
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
            // Miuix supplies native controls while sharing Songlass navigation and player mechanics.
            api(projects.themeLiquidglass)
            implementation(libs.miuix.ui)
            implementation(libs.miuix.blur)
            implementation(libs.compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "dev.cmdrchara.songlass.theme.miuix.resources"
}
