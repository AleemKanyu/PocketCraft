import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

fun parseGitHubRepo(remoteUrl: String?): Pair<String, String>? {
    if (remoteUrl.isNullOrBlank()) return null
    val cleaned = remoteUrl.removeSuffix(".git").trim()
    val https = Regex("https://github\\.com/([^/]+)/([^/]+)$", RegexOption.IGNORE_CASE)
    val ssh = Regex("git@github\\.com:([^/]+)/([^/]+)$", RegexOption.IGNORE_CASE)
    val match = https.find(cleaned) ?: ssh.find(cleaned) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

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

val configuredPrivacyPolicyUrl = localProperties.getProperty("privacyPolicyUrl")
    ?: System.getenv("POCKETCRAFT_PRIVACY_POLICY_URL")
val configuredTermsOfUseUrl = localProperties.getProperty("termsOfUseUrl")
    ?: System.getenv("POCKETCRAFT_TERMS_OF_USE_URL")

val gitRemoteUrl = runCatching {
    val process = ProcessBuilder("git", "config", "--get", "remote.origin.url")
        .directory(rootProject.rootDir)
        .start()
    process.inputStream.bufferedReader().use { it.readText().trim() }
}.getOrNull()

val (githubRepoOwner, githubRepoName) = parseGitHubRepo(gitRemoteUrl)
    ?.let { (owner, repo) -> owner to repo.trimEnd('_') }
    ?: ("AleemKanyu" to "PocketCraft")

val legalPrivacyPolicyUrl = configuredPrivacyPolicyUrl?.trim().takeUnless { it.isNullOrBlank() }
    ?: "https://pocketcraft.online/privacy"
val legalTermsOfUseUrl = configuredTermsOfUseUrl?.trim().takeUnless { it.isNullOrBlank() }
    ?: "https://pocketcraft.online/terms"

val autoVersionCode = (System.currentTimeMillis() / 60000).toInt()

android {
    namespace = "com.pocketcraft.server"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.pocketcraft.server"
        minSdk = 26
        targetSdk = 35
        versionCode = autoVersionCode
        versionName = "1.0.0"

        buildConfigField("String", "RELAY_PUBLIC_DOMAIN", "\"joinmc.link\"")
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$githubRepoOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$githubRepoName\"")
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

    androidResources {
        noCompress += listOf("jar", "jks", "xz", "gz")
    }

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf("src/main/assets"))
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = configuredReleaseKeystorePath
            val storePwd = configuredReleaseStorePassword
            val keyAlias = configuredReleaseKeyAlias
            val keyPwd = configuredReleaseKeyPassword

            storeFile = if (keystorePath.isNullOrBlank()) null else file(keystorePath)
            storePassword = storePwd
            this.keyAlias = keyAlias
            keyPassword = keyPwd
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            
            isMinifyEnabled = true
            isShrinkResources = true
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
        checkReleaseBuilds = true
        abortOnError = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

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

    implementation("com.google.zxing:core:3.5.3")
}
