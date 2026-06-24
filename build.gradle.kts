plugins {
    id("com.gradleup.shadow") version "9.4.2" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("xyz.jpenilla.run-paper") version "3.0.2" apply false
    id("de.eldoria.plugin-yml.paper") version "0.9.0" apply false
}

allprojects {
    group = "de.oliver"
    description = "TwiNpcs, a FancyNpcs fork"

    repositories {
        mavenLocal()
        mavenCentral()

        maven(url = "https://maven.fancyspaces.net/fancyinnovations/releases")
        maven(url = "https://maven.fancyspaces.net/fancyinnovations/snapshots")
        maven(url = "https://maven.fancyspaces.net/origami/releases")
        maven(url = "https://repo.fancyinnovations.com/releases")
        maven(url = "https://repo.fancyinnovations.com/snapshots")

        maven(url = "https://repo.lushplugins.org/releases")
        maven(url = "https://repo.papermc.io/repository/maven-public/")
//        maven(url = "https://jitpack.io")
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "net.kyori"
                && requested.name == "adventure-text-serializer-ansi"
                && requested.version.isNullOrBlank()
            ) {
                useVersion("5.1.1")
                because("Paper dev bundles omit the version and rely on their platform constraint")
            }
        }
    }
}
