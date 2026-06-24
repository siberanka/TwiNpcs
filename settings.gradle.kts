pluginManagement {
    repositories {
        mavenLocal()
        maven(url = "https://artifactory.papermc.io/artifactory/snapshots")
//        maven(url = "https://artifactory.papermc.io/artifactory/releases")
        maven(url = "https://maven.fancyspaces.net/fancyinnovations/snapshots")
        maven(url = "https://maven.fancyspaces.net/fancyinnovations/releases")
        maven(url = "https://repo.fancyinnovations.com/releases")
        gradlePluginPortal()
        mavenLocal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "TwiNpcs"

include(":plugins:twinpcs")
include(":plugins:twinpcs:twinpcs-api")
include(":plugins:twinpcs:implementation_26_2")
include(":plugins:twinpcs:implementation_26_1_2")
include(":plugins:twinpcs:implementation_1_21_11")
include(":plugins:twinpcs:implementation_1_21_9")
include(":plugins:twinpcs:implementation_1_21_6")
include(":plugins:twinpcs:implementation_1_21_5")

include(":libraries:common")
include(":libraries:jdb")
include(":libraries:config")

