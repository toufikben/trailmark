pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "trailmark"

include(":core:model", ":core:geo", ":core:time", ":engine", ":data", ":recording", ":app")
