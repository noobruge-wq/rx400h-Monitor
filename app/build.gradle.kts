plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionName = "0.3.2"

val gitExecutable = providers.environmentVariable("RX400H_GIT_EXECUTABLE")
    .orElse("git")
    .get()

fun checkedGitOutput(vararg arguments: String): String {
    val output = providers.exec {
        workingDir(rootDir)
        commandLine(gitExecutable, *arguments)
        isIgnoreExitValue = true
    }
    val result = output.result.get()
    check(result.exitValue == 0) {
        val detail = output.standardError.asText.get().trim()
        "Git provenance command failed (${result.exitValue}): ${arguments.joinToString(" ")}" +
            if (detail.isBlank()) "" else " — $detail"
    }
    return output.standardOutput.asText.get()
}

val gitCommit = checkedGitOutput("rev-parse", "HEAD").trim()
check(gitCommit.matches(Regex("[0-9a-fA-F]{40}"))) {
    "Git provenance returned an invalid commit ID: $gitCommit"
}

val gitDirty = checkedGitOutput("status", "--porcelain", "--untracked-files=normal").isNotBlank()

android {
    namespace = "com.guanyu.rx400hprobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guanyu.rx400hprobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = appVersionName
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("boolean", "GIT_DIRTY", gitDirty.toString())
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
    buildFeatures { buildConfig = true }
}


dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    testImplementation("junit:junit:4.13.2")
}
