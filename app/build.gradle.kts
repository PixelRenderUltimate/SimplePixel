import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Keystore dibaca dari environment (GitHub Secrets) atau dari keystore.properties lokal.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun secret(key: String, envName: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(envName)

val appVersionName = "1.0.1"

android {
    namespace = "com.simplereso.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simplereso.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = appVersionName
    }

    val storePath = secret("storeFile", "KEYSTORE_PATH")
    signingConfigs {
        if (storePath != null && file(storePath).exists()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = secret("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "KEY_ALIAS")
                keyPassword = secret("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Kalau keystore tidak tersedia, build release tetap jalan dengan debug signing.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "simple-reso-v$appVersionName.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
