pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        ivy {
            url = uri("https://github.com/2dust/AndroidLibXrayLite/releases/download/")
            patternLayout {
                artifact("v[revision]/[artifact].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.github.2dust", "libv2ray")
            }
        }
    }
}

rootProject.name = "EhsanNet"
include(":app")
