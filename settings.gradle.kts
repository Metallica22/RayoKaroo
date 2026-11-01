pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR") ?: "anonymous")
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
            }
        }
    }
}

rootProject.name = "RayoKaroo"
include(":app")
