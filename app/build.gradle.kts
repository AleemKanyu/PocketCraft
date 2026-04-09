import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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
val configuredGitHubRepoOwner = localProperties.getProperty("githubRepoOwner")
    ?: System.getenv("POCKETCRAFT_GITHUB_REPO_OWNER")
val configuredGitHubRepoName = localProperties.getProperty("githubRepoName")
    ?: System.getenv("POCKETCRAFT_GITHUB_REPO_NAME")
val configuredUpdateManifestUrl = localProperties.getProperty("updateManifestUrl")
    ?: System.getenv("POCKETCRAFT_UPDATE_MANIFEST_URL")
val configuredPrivacyPolicyUrl = localProperties.getProperty("privacyPolicyUrl")
    ?: System.getenv("POCKETCRAFT_PRIVACY_POLICY_URL")
val configuredTermsOfUseUrl = localProperties.getProperty("termsOfUseUrl")
    ?: System.getenv("POCKETCRAFT_TERMS_OF_USE_URL")

val hasConfiguredReleaseSigning =
    !configuredReleaseKeystorePath.isNullOrBlank() &&
        !configuredReleaseStorePassword.isNullOrBlank() &&
        !configuredReleaseKeyAlias.isNullOrBlank() &&
        !configuredReleaseKeyPassword.isNullOrBlank()

val githubRepoOwner = configuredGitHubRepoOwner?.trim().takeUnless { it.isNullOrBlank() }
    ?: "AleemKanyu"
val githubRepoName = configuredGitHubRepoName?.trim().takeUnless { it.isNullOrBlank() }
    ?: "PocketCraft"

val legalPrivacyPolicyUrl = configuredPrivacyPolicyUrl?.trim().takeUnless { it.isNullOrBlank() }
    ?: "https://pocketcraft.online/privacy"
val legalTermsOfUseUrl = configuredTermsOfUseUrl?.trim().takeUnless { it.isNullOrBlank() }
    ?: "https://pocketcraft.online/terms"

android {
    namespace = "com.pocketcraft.server"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketcraft.server"
        minSdk = 25
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.0-Beta"
        buildConfigField("String", "RELAY_PUBLIC_DOMAIN", "\"joinmc.link\"")
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$githubRepoOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$githubRepoName\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"${configuredUpdateManifestUrl.orEmpty()}\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$legalPrivacyPolicyUrl\"")
        buildConfigField("String", "TERMS_OF_USE_URL", "\"$legalTermsOfUseUrl\"")
        buildConfigField("String", "LEGAL_POLICY_VERSION", "\"2026-04-06\"")

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
    // Keep archive/runtime assets and ad-viewer JS files uncompressed.
    // AGP 8.8.0 can mis-handle the Ads SDK OMID JS assets during compressReleaseAssets.
    androidResources {
        noCompress += listOf("jar", "jks", "xz", "gz", "js")
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
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
        jvmTarget.set(JvmTarget.JVM_21)
    }
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
    implementation(libs.firebase.crashlytics)
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-inappmessaging-display-ktx")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.zxing:core:3.5.3")
}
