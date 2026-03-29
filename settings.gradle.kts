pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fusion-mock-system"

include(":shared")
include(":okhttp")
include(":ktor")
include(":android-sample")
include(":desktop-sample")
