plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.utmity.titanime"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.utmity.titanime"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
