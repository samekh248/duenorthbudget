import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

// Drives the release-like app builds on a phone or emulator: records the baseline profile and measures
// cold start and scrolling against the constitution's budgets (Principle I). See benchmark/README.md.
android {
    namespace = "app.duenorth.budget.benchmark"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        // Startup and frame metrics need API 28+; the profile recorder needs 28+ too.
        minSdk = 28
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // CI measures on an emulator: its numbers gate regressions, a Pixel 6a class phone gates the 1 s budget.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            // The full AOSP image, not ATD: the profile recorder needs the platform's profile tooling.
            systemImageSource = "aosp"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    // The emulator above by default; -PbenchmarkOnDevice records on the phone plugged in instead.
    val onDevice = providers.gradleProperty("benchmarkOnDevice").isPresent
    if (!onDevice) managedDevices += "pixel6Api34"
    useConnectedDevices = onDevice
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
