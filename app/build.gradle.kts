plugins {
    id("com.android.application")
}

android {
    namespace = "uk.co.crackinevents.showroomtv"
    compileSdk = 34

    defaultConfig {
        applicationId = "uk.co.crackinevents.showroomtv"
        minSdk = 22
        targetSdk = 33
        versionCode = 6
        versionName = "1.0.5"
    }

    signingConfigs {
        create("crackin") {
            storeFile = file("crackin-showroom-tv.jks")
            storePassword = "CrackinTV2026"
            keyAlias = "crackin"
            keyPassword = "CrackinTV2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("crackin")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
