import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core)
    implementation(projects.designsystem)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.guava)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.extractor)
    implementation(libs.media3.session)
    implementation(libs.taglib)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.compose.uiToolingPreview)
}

// CI supplies these only to the signed build step; local/debug builds need no secrets.
val releaseSigning = listOf(
    "SONGLASS_SIGNING_STORE_FILE",
    "SONGLASS_SIGNING_STORE_PASSWORD",
    "SONGLASS_SIGNING_KEY_ALIAS",
    "SONGLASS_SIGNING_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigning.values.all { !it.isNullOrBlank() }
require(hasReleaseSigning || releaseSigning.values.all { it == null }) {
    "Provide all four SONGLASS_SIGNING_* variables together, or leave them all unset."
}

android {
    namespace = "dev.cmdrchara.songlass"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.cmdrchara.songlass"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.2.0-alpha.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigning["SONGLASS_SIGNING_STORE_FILE"]))
                storePassword = releaseSigning["SONGLASS_SIGNING_STORE_PASSWORD"]
                keyAlias = releaseSigning["SONGLASS_SIGNING_KEY_ALIAS"]
                keyPassword = releaseSigning["SONGLASS_SIGNING_KEY_PASSWORD"]
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
