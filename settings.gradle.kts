pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Fijar AGP — impide que Android Studio lo actualice solo
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useVersion("8.7.3")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Jagoo"
include(":app")
