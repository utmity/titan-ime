plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf") version "0.9.4"
}

// CI versioning and signing (see docs/apk-signing.md). GitHub Actions
// restores these from repository secrets; when present, every CI build
// shares one stable signing key and a monotonically increasing
// versionCode, so a CI APK can update an existing install in place.
// Without them (local development), builds keep the pinned development
// versionCode and the standard per-machine Android debug key — which is
// exactly why CI APKs built without the secrets can never update each
// other: each ephemeral runner generates a different debug key.
val ciVersionCode = System.getenv("TITAN_VERSION_CODE")?.toIntOrNull()
val ciKeystoreFile = System.getenv("TITAN_KEYSTORE_FILE")
android {
    namespace = "dev.utmity.titanime"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.utmity.titanime"
        minSdk = 28
        targetSdk = 35
        versionCode = ciVersionCode ?: 2
        versionName = "0.1.1-pr2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["imeAppLabel"] = "Titan IME"
        manifestPlaceholders["imeSettingsLabel"] = "Titan IME 設定"

    }
    if (ciKeystoreFile != null) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystoreFile)
                storePassword = System.getenv("TITAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TITAN_KEY_ALIAS")
                keyPassword = System.getenv("TITAN_KEY_PASSWORD")
                if (ciKeystoreFile.endsWith(".p12", ignoreCase = true) ||
                    ciKeystoreFile.endsWith(".pfx", ignoreCase = true)) {
                    storeType = "PKCS12"
                }
            }
        }
    }
    buildTypes {
        if (ciKeystoreFile != null) {
            val ciSigning = signingConfigs.getByName("ci")
            getByName("debug") { signingConfig = ciSigning }
            getByName("release") { signingConfig = ciSigning }
        }
        // A separate package avoids replacing a differently signed installed IME.
        // Preview has independent settings/dictionary data, and can be removed safely.
        // It inherits the debug build type, so it shares the CI signer too.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            manifestPlaceholders["imeAppLabel"] = "Titan IME Preview"
            manifestPlaceholders["imeSettingsLabel"] = "Titan IME Preview 設定"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.autofill:autofill:1.1.0")
    implementation("com.google.protobuf:protobuf-javalite:4.34.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
}
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.34.1" }
    generateProtoTasks {
        all().configureEach {
            builtins { maybeCreate("java").option("lite") }
        }
    }
}
val verifyMozcAssets by tasks.registering {
    doLast {
        for (path in listOf("src/main/jniLibs/arm64-v8a/libmozc.so", "src/main/assets/mozc.data")) {
            check(file(path).isFile) { "Missing $path. Build Mozc and run scripts/import-mozc.ps1 first." }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyMozcAssets) }
