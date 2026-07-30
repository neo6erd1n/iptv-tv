import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.iptvtv"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.iptvtv"
        minSdk = 23
        targetSdk = 36
        versionCode = 13
        versionName = "0.13.0"

        buildConfigField(
            "String",
            "GITHUB_REPOSITORY",
            "\"neo6erd1n/iptv-tv\"",
        )
    }

    val keystorePath = System.getenv("IPTV_KEYSTORE_PATH")
    val keystorePassword = System.getenv("IPTV_KEYSTORE_PASSWORD")
    val keyAliasValue = System.getenv("IPTV_KEY_ALIAS")
    val keyPasswordValue = System.getenv("IPTV_KEY_PASSWORD")

    if (
        keystorePath != null &&
        keystorePassword != null &&
        keyAliasValue != null &&
        keyPasswordValue != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
        buildTypes {
            release {
                signingConfig = signingConfigs.getByName("release")
                isMinifyEnabled = false
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
    debugImplementation(libs.compose.ui.tooling)
}
