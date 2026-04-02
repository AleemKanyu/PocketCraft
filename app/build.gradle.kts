import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val configuredReleaseKeystorePath = localProperties.getProperty("releaseKeystorePath")
    ?: System.getenv("POCKETCRAFT_RELEASE_KEYSTORE")
val configuredReleaseStorePassword = localProperties.getProperty("releaseStorePassword")
    ?: System.getenv("POCKETCRAFT_RELEASE_STORE_PASSWORD")
val configuredReleaseKeyAlias = localProperties.getProperty("releaseKeyAlias")
    ?: System.getenv("POCKETCRAFT_RELEASE_KEY_ALIAS")
val configuredReleaseKeyPassword = localProperties.getProperty("releaseKeyPassword")
    ?: System.getenv("POCKETCRAFT_RELEASE_KEY_PASSWORD")

val hasConfiguredReleaseSigning =
    !configuredReleaseKeystorePath.isNullOrBlank() &&
        !configuredReleaseStorePassword.isNullOrBlank() &&
        !configuredReleaseKeyAlias.isNullOrBlank() &&
        !configuredReleaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.pocketcraft.server"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketcraft.server"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "RELAY_PUBLIC_DOMAIN", "\"joinmc.link\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    // Prevent .so compression — compressed .so files cannot be dlopen'd
    androidResources {
        noCompress += listOf("so", "jar", "jks", "xz", "gz")
    }

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf("src/main/assets"))
        }
    }

    signingConfigs {
        if (hasConfiguredReleaseSigning) {
            create("release") {
                storeFile = file(configuredReleaseKeystorePath!!)
                storePassword = configuredReleaseStorePassword
                keyAlias = configuredReleaseKeyAlias
                keyPassword = configuredReleaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasConfiguredReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }



    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    jvmToolchain(17)
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("org.tukaani:xz:1.9")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Networking
    implementation(libs.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Retrofit + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation("com.google.zxing:core:3.5.3")
}
