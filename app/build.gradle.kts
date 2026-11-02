plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.brainrotnotif"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.brainrotnotif"
        minSdk = 30
        targetSdk = 36
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.0.${versionCode}"
    }

    // A stable key lets each newer APK from the F-Droid repo install over the previous one.
    // The key lives outside the repo; without the variables a release build stays unsigned.
    signingConfigs {
        create("release") {
            System.getenv("BRAINROT_KEYSTORE_FILE")?.let { storeFile = file(it) }
            storePassword = System.getenv("BRAINROT_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("BRAINROT_KEY_ALIAS")
            keyPassword = System.getenv("BRAINROT_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            if (System.getenv("BRAINROT_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
}
