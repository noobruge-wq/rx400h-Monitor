plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.guanyu.rx400hprobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guanyu.rx400hprobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.1.7"
    }

    signingConfigs {
        create("githubDebug") {
            storeFile = rootProject.file(".github/signing/rx400h-debug.keystore")
            storePassword = "android"
            keyAlias = "rx400hdebug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("githubDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}


dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
