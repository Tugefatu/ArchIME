plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "org.archime.ime"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.archime.ime"
        minSdk = 26
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0-rewrite"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
