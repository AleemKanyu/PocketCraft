import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
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
    ?: "https://pockethost.online/privacy"
val legalTermsOfUseUrl = configuredTermsOfUseUrl?.trim().takeUnless { it.isNullOrBlank() }
    ?: "https://pockethost.online/terms"
val configuredSupportEmail = localProperties.getProperty("supportEmail")
    ?: System.getenv("POCKETHOST_SUPPORT_EMAIL")
    ?: "support@pockethost.online"
val officialWebsiteUrl = localProperties.getProperty("officialWebsiteUrl")
    ?: System.getenv("POCKETHOST_WEBSITE_URL")
    ?: "https://pockethost.online"

val autoVersionCode = (System.currentTimeMillis() / 60000).toInt()
val allowDebugSigningForRelease = providers.gradleProperty("pocketcraftAllowDebugSigning")
    .map { it.toBoolean() }
    .getOrElse(false)
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
        versionName = "1.2.4"

        buildConfigField("String", "RELAY_PUBLIC_DOMAIN", "\"joinmc.link\"")
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$githubRepoOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$githubRepoName\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$legalPrivacyPolicyUrl\"")
        buildConfigField("String", "TERMS_OF_USE_URL", "\"$legalTermsOfUseUrl\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"$configuredSupportEmail\"")
        buildConfigField("String", "WEBSITE_URL", "\"$officialWebsiteUrl\"")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
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
            val hasReleaseKey = releaseSigning.storeFile != null &&
                !configuredReleaseStorePassword.isNullOrBlank()

            // Never silently fall back to the debug keystore here. The debug key
            // is the shared, publicly known Android one: an APK signed with it
            // cannot be installed over a properly signed copy, so shipping one
            // breaks updates for every existing user. Allow it only when the
            // build explicitly opts in with -PpocketcraftAllowDebugSigning=true,
            // which is for local smoke tests, never for anything published.
            if (!hasReleaseKey && !allowDebugSigningForRelease) {
                throw GradleException(
                    "Release signing key not found, refusing to build an unsigned-for-release APK.\n" +
                        "Expected a keystore via keystore.properties (storeFile/storePassword), " +
                        "local.properties (releaseKeystorePath/releaseStorePassword), or the " +
                        "POCKETCRAFT_RELEASE_KEYSTORE / POCKETCRAFT_RELEASE_STORE_PASSWORD " +
                        "environment variables.\n" +
                        "To build a throwaway debug-signed release locally, pass " +
                        "-PpocketcraftAllowDebugSigning=true -- never publish that artifact."
                )
            }

            signingConfig = if (hasReleaseKey) {
                releaseSigning
            } else {
                logger.warn(
                    "WARNING: building release with the DEBUG signing key because " +
                        "-PpocketcraftAllowDebugSigning=true was passed. Do not publish this APK."
                )
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

    applicationVariants.all {
        val variant = this
        outputs.all {
            val outputImpl = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val suffix = if (variant.buildType.name == "release") "external" else "debug"
            outputImpl?.outputFileName = "PocketHost-$suffix.apk"
        }
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
    enabled = false
}

tasks.register("verifyBundledJreMetadata") {
    description = "Verifies that every bundled JRE was built by PocketCraft and contains no third-party runtime binaries."
    group = "verification"

    val assetsDir = file("src/main/assets")
    inputs.dir(assetsDir)

    doLast {
        // Identifiers that must never appear in a runtime we ship. The last two
        // are the important ones: "adhoc.root.openjdk" is the build stamp baked
        // into the Anvil-MC/ARM-MC JDK 25, and the hash is that build's libjvm.so.
        // Both survive any amount of editing of the plain-text `release` file,
        // which is exactly how the previous version of this check was fooled.
        val forbiddenStrings = listOf(
            "ARM-MC", "arm-mc.com", "ANVIL-MC", "anvil-mc.com",
            "pojavlauncher", "pojav", "adhoc.root.openjdk"
        )
        val forbiddenLibjvmHashes = mapOf(
            "2138beda82c1d50dfb2984237a536b9a5dccc1b555e0424d32b896137d2ee0f5"
                to "Anvil-MC / ARM-MC JDK 25"
        )
        // Our own builds pass --with-version-opt=pocketcraft, so this lands in
        // the VM version string inside libjvm.so.
        val requiredVendorMarker = "pocketcraft"

        // Which bundled runtimes we build ourselves, and which are upstream
        // artifacts we redistribute.
        //
        // jre25 is ours: tools/jdk25-android/ compiles it, so it must carry our
        // vendor marker. jre17 and jre21 are public release builds from the
        // PojavLauncher lineage (their libjvm.so reports "adhoc.runner.openjdk",
        // i.e. built by a CI runner and published as a release, not lifted off
        // someone's machine). GPLv2+CE allows redistributing those, so they are
        // allowlisted here rather than required to be ours -- but the forbidden
        // identifier and known-bad-hash checks below still apply to them.
        //
        // Removing an entry from this list is the right move once we build that
        // version ourselves too.
        val upstreamRedistributions = setOf("jre17", "jre21")

        fun bytesContain(haystack: ByteArray, needle: String): Boolean {
            val n = needle.lowercase().toByteArray(Charsets.US_ASCII)
            if (n.isEmpty() || haystack.size < n.size) return false
            outer@ for (i in 0..(haystack.size - n.size)) {
                for (j in n.indices) {
                    val c = haystack[i + j].toInt().toChar().lowercaseChar().code.toByte()
                    if (c != n[j]) continue@outer
                }
                return true
            }
            return false
        }

        // 1. Plain-text release descriptors.
        assetsDir.walkTopDown().filter { it.isFile && it.name == "release" }.forEach { file ->
            val text = file.readText()
            forbiddenStrings.forEach { forbidden ->
                if (text.contains(forbidden, ignoreCase = true)) {
                    throw GradleException(
                        "Forbidden third-party identifier '$forbidden' in ${file.path}"
                    )
                }
            }
            // A scrubbed SOURCE line. Upstream builds record a real revision here;
            // ".:git:openjdk" is what is left when someone blanks it by hand.
            if (text.contains("SOURCE=\".:git:openjdk\"")) {
                throw GradleException(
                    "${file.path} has a blanked SOURCE line. Restore the real revision " +
                    "from the runtime it describes -- GPLv2 requires upstream notices be " +
                    "kept intact, and a blanked SOURCE is exactly what gets noticed."
                )
            }
        }

        // 2. The actual JVM binary inside each native archive. A `release` file
        //    describes a binary; it is not evidence about it.
        val archives = assetsDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("bin-") && it.name.endsWith(".tar.xz") }
            .toList()

        if (archives.isEmpty()) {
            logger.lifecycle("Bundled JRE audit: no native runtime archives present, nothing to verify.")
            return@doLast
        }

        val tmp = File(layout.buildDirectory.get().asFile, "jre-audit").apply { mkdirs() }
        archives.forEach { archive ->
            val extracted = File(tmp, "${archive.parentFile.name}-${archive.name}-libjvm.so")
            val ok = providers.exec {
                commandLine("tar", "-xJOf", archive.absolutePath, "./lib/server/libjvm.so")
            }.let { spec ->
                runCatching {
                    extracted.writeBytes(spec.standardOutput.asBytes.get())
                    extracted.length() > 0
                }.getOrDefault(false)
            }
            if (!ok) {
                throw GradleException(
                    "Could not read ./lib/server/libjvm.so out of ${archive.path}. " +
                    "Every shipped runtime must expose its JVM for verification."
                )
            }

            val sha = MessageDigest.getInstance("SHA-256")
                .digest(extracted.readBytes())
                .joinToString("") { b -> "%02x".format(b) }
            forbiddenLibjvmHashes[sha]?.let { owner ->
                throw GradleException(
                    "${archive.path} ships the $owner libjvm.so (sha256 $sha). " +
                    "Build our own with tools/jdk25-android/ instead."
                )
            }

            val bytes = extracted.readBytes()
            forbiddenStrings.forEach { forbidden ->
                if (bytesContain(bytes, forbidden)) {
                    throw GradleException(
                        "Forbidden identifier '$forbidden' is compiled into ${archive.path}'s libjvm.so."
                    )
                }
            }
            // A loose `release` sitting next to the archive must agree with the one
            // inside it. Editing only the outer copy is precisely how jre17 came to
            // claim IMPLEMENTOR="The OpenJDK Community" while its own tarball still
            // said "N/A" -- the archive is the authority, the loose file is a
            // convenience copy.
            val looseRelease = File(archive.parentFile, "release")
            if (looseRelease.isFile) {
                val inner = runCatching {
                    providers.exec {
                        commandLine("tar", "-xJOf", archive.absolutePath, "./release")
                    }.standardOutput.asText.get()
                }.getOrNull()
                if (inner != null && inner.isNotBlank() &&
                    inner.trim() != looseRelease.readText().trim()
                ) {
                    throw GradleException(
                        "${looseRelease.path} does not match the release file inside " +
                        "${archive.name}. The archive is authoritative; do not hand-edit " +
                        "the loose copy."
                    )
                }
            }

            val runtimeDir = archive.parentFile.name
            if (runtimeDir in upstreamRedistributions) {
                logger.lifecycle(
                    "Bundled JRE audit: $runtimeDir/${archive.name} is an allowlisted upstream " +
                    "redistribution (sha256 $sha); no forbidden identifiers found."
                )
            } else if (!bytesContain(bytes, requiredVendorMarker)) {
                throw GradleException(
                    "$runtimeDir/${archive.name}'s libjvm.so does not identify itself as a " +
                    "PocketCraft build. Build it with tools/jdk25-android/, which sets the vendor " +
                    "at configure time, or add '$runtimeDir' to upstreamRedistributions if it is " +
                    "genuinely an upstream release we are entitled to redistribute."
                )
            } else {
                logger.lifecycle("Bundled JRE audit: $runtimeDir/${archive.name} verified as a PocketCraft build (sha256 $sha).")
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("verifyBundledJreMetadata")
}


