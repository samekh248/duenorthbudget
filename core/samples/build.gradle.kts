plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The sample budgets debug builds offer. Only debug builds of :app depend on this module, so release builds
// carry none of the sample data. Its tests run with :core:budget's, which has the SQLite test harness.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:budget"))
    implementation(libs.serialization.json)
}
