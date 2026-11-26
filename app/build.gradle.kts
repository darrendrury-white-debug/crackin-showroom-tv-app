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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
