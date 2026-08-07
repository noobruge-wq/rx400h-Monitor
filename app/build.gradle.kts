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
        versionCode = 9
        versionName = "0.1.10"
    }

    // Fixed project debug signing key, retained from v0.1.6-v0.1.8.
    // This is a test/debug key only; it exists solely so GitHub Actions builds
    // can update previously installed RX400h Protocol Probe debug builds.
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
