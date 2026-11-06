plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tj90.unduanki"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tj90.unduanki"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("FAMILY_VERSION_CODE") ?: "1").toInt()
        versionName = "0.1-" + (System.getenv("GITHUB_SHA")?.take(7) ?: "local")
    }
    signingConfigs {
        create("family") {
            val path = System.getenv("ANDROID_KEYSTORE_PATH")
            if (path != null) storeFile = file(path)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; resValue("string", "app_name", "Mischief Island Test") }
        release { signingConfig = signingConfigs.getByName("family"); resValue("string", "app_name", "Mischief Island") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
