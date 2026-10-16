pluginManagement {
    repositories {
        google()
        // Set by the cloud session hook (.claude/hooks/gradle-dep-cache.sh) when
        // the network allows it, to stay clear of Maven Central's rate limit.
        System.getenv("MAVEN_CENTRAL_MIRROR")?.let { maven(it) }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        System.getenv("MAVEN_CENTRAL_MIRROR")?.let { maven(it) }
        mavenCentral()
    }
}

rootProject.name = "DueNorthBudget"

include(":app")
include(":core:budget")
include(":core:design")
