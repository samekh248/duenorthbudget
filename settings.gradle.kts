pluginManagement {
    repositories {
        google()
        // Google's copy of Maven Central, ahead of Central itself: Claude cloud
        // sessions share IPs that Central often rate-limits (429).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}

rootProject.name = "DueNorthBudget"

include(":app")
include(":core:budget")
include(":core:design")
include(":core:samples")
