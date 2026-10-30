import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "app.duenorth.budget"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "app.duenorth.budget"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        // A release build can set these with -PversionCode=3 -PversionName=0.1.2 instead of an edit here.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 2
        versionName = findProperty("versionName") as String? ?: "0.1.1"
    }

    signingConfigs {
        // One shared debug key, so a debug APK from any machine installs over another.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The release key is private and never in this repository: its path and passwords come from
        // a keystore.properties named by DUENORTH_SIGNING (or at the repo root), or DUENORTH_* variables.
        // Without one, the release APK is left unsigned.
        val signing =
            Properties().apply {
                val named = System.getenv("DUENORTH_SIGNING")?.let(::File) ?: rootProject.file("keystore.properties")
                if (named.isFile) named.inputStream().use(::load)
            }

        fun signingValue(
            key: String,
            env: String,
        ): String? = signing.getProperty(key) ?: System.getenv(env)
        val releaseStore = signingValue("storeFile", "DUENORTH_KEYSTORE")
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signingValue("storePassword", "DUENORTH_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DUENORTH_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DUENORTH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty(
                "robolectric.dependency.repo.url",
                "https://maven-central.storage-download.googleapis.com/maven2",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:budget"))
    implementation(project(":core:design"))
    debugImplementation(project(":core:samples"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.security.crypto)
    implementation(libs.browser)
    implementation(libs.work.runtime)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
