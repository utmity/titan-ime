plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf") version "0.9.4"
}
android {
    namespace = "dev.utmity.titanime"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.utmity.titanime"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
