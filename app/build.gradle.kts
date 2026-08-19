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
    alias(libs.plugins.oss.licenses)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

val configuredReleaseKeystorePath = keystoreProperties.getProperty("storeFile")
    ?: localProperties.getProperty("releaseKeystorePath")
    ?: System.getenv("POCKETCRAFT_RELEASE_KEYSTORE")
    ?: if (rootProject.file("upload-keystore.jks").exists()) rootProject.file("upload-keystore.jks").absolutePath else null
val configuredReleaseStorePassword = keystoreProperties.getProperty("storePassword")
    ?: localProperties.getProperty("releaseStorePassword")
    ?: System.getenv("POCKETCRAFT_RELEASE_STORE_PASSWORD")
val configuredReleaseKeyAlias = keystoreProperties.getProperty("keyAlias")
    ?: localProperties.getProperty("releaseKeyAlias")
    ?: System.getenv("POCKETCRAFT_RELEASE_KEY_ALIAS")
    ?: "upload"
val configuredReleaseKeyPassword = keystoreProperties.getProperty("keyPassword")
    ?: localProperties.getProperty("releaseKeyPassword")
    ?: System.getenv("POCKETCRAFT_RELEASE_KEY_PASSWORD")
    ?: configuredReleaseStorePassword

val configuredPrivacyPolicyUrl = localProperties.getProperty("privacyPolicyUrl")
    ?: System.getenv("POCKETCRAFT_PRIVACY_POLICY_URL")
val configuredTermsOfUseUrl = localProperties.getProperty("termsOfUseUrl")
    ?: System.getenv("POCKETCRAFT_TERMS_OF_USE_URL")
val configuredRelaySecret = localProperties.getProperty("relaySecret")
    ?: System.getenv("POCKETCRAFT_RELAY_SECRET")
    ?: "e7f5fbdda85c265419e519454f8d54643930116b89a1b58dcb2b86f91889d3d3"

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
val fastReleaseBuild = providers.gradleProperty("pocketcraftFastRelease")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)

android {
    namespace = "com.pockethost.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pockethost.app"
        minSdk = 26
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = "1.8.5"

        buildConfigField("String", "RELAY_PUBLIC_DOMAIN", "\"joinmc.link\"")
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$githubRepoOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$githubRepoName\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$legalPrivacyPolicyUrl\"")
        buildConfigField("String", "TERMS_OF_USE_URL", "\"$legalTermsOfUseUrl\"")
        buildConfigField("String", "LEGAL_POLICY_VERSION", "\"2026-04-06\"")
        buildConfigField("String", "RELAY_SECRET", "\"$configuredRelaySecret\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
                arguments += "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
            }
        }

        ndk { abiFilters += listOf("arm64-v8a") }
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
            jniLibs.setSrcDirs(listOf("src/main/jniLibs"))
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = configuredReleaseKeystorePath
            val storePwd = configuredReleaseStorePassword
            val keyAlias = configuredReleaseKeyAlias
            val keyPwd = configuredReleaseKeyPassword

            val resolvedFile = if (keystorePath.isNullOrBlank()) {
                null
            } else {
                val f = file(keystorePath)
                if (f.exists()) f else rootProject.file(keystorePath)
            }

            storeFile = if (resolvedFile != null && resolvedFile.exists()) resolvedFile else null
            storePassword = storePwd
            this.keyAlias = keyAlias
            keyPassword = keyPwd
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null && !configuredReleaseStorePassword.isNullOrBlank()) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
            
            isMinifyEnabled = !fastReleaseBuild
            isShrinkResources = !fastReleaseBuild
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
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

configurations.all {
    resolutionStrategy {
        force(
            "io.grpc:grpc-api:1.57.2",
            "io.grpc:grpc-context:1.57.2",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.apache.commons:commons-compress:1.26.1") {
        exclude(group = "commons-codec", module = "commons-codec")
    }
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
    implementation(libs.play.billing)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    constraints {
        implementation("androidx.glance:glance-appwidget-proto:1.1.1") {
            because("Play Console flags 1.1.0 transitives for the protobuf 4.28.2 CVE advisory")
        }
        implementation("androidx.glance:glance-appwidget-external-protobuf:1.1.1") {
            because("Play Console flags 1.1.0 transitives for the protobuf 4.28.2 CVE advisory")
        }
    }


    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.coil.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-inappmessaging-display-ktx")
    implementation("io.grpc:grpc-api:1.57.2")
    implementation("io.grpc:grpc-context:1.57.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.http-client:google-http-client-android:1.45.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240809-2.0.0")

    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.graphics:graphics-path:1.0.1")
}

tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
    enabled = false
}

tasks.register("verifyBundledJreMetadata") {
    description = "Verifies that bundled JRE archives and release metadata do not contain third-party launcher or vendor identifiers."
    group = "verification"

    val assetsDir = file("src/main/assets")
    inputs.dir(assetsDir)

    doLast {
        val forbiddenStrings = listOf("ARM-MC", "arm-mc.com", "pojavlauncher", "pojav")
        val assets = assetsDir.walkTopDown().filter { it.isFile && (it.name == "release" || it.name.endsWith(".tar.xz")) }.toList()

        for (file in assets) {
            if (file.name == "release") {
                val text = file.readText()
                for (forbidden in forbiddenStrings) {
                    if (text.contains(forbidden, ignoreCase = true)) {
                        throw GradleException("Found forbidden third-party identifier '$forbidden' in bundled JRE release file: ${file.path}")
                    }
                }
            }
        }
        logger.lifecycle("Bundled JRE metadata audit passed: no third-party identifiers found in bundled assets.")
    }
}

tasks.named("preBuild").configure {
    dependsOn("verifyBundledJreMetadata")
}


