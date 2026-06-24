import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("java-library")
    id("maven-publish")
    id("xyz.jpenilla.run-paper")
    id("com.gradleup.shadow")
    id("de.eldoria.plugin-yml.paper")
}

runPaper.folia.registerTask()

allprojects {
    group = "de.oliver"
    version = getFNVersion()
    description = "Professional packet-based NPC plugin forked from FancyNpcs"

    repositories {
        maven(url = "https://repo.inventivetalent.org/repository/maven-snapshots/") // for MineSkin
        maven(url = "https://repo.extendedclip.com/releases/") // for PlaceholderAPI
        maven(url = "https://maven.enginehub.org/repo/") // for WorldEdit
        maven(url = "https://maven.citizensnpcs.co/repo") // for Citizens
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    implementation(project(":plugins:twinpcs:twinpcs-api")) {
        isTransitive = false
    }
    implementation(project(":plugins:twinpcs:implementation_26_2"))
    implementation(project(":plugins:twinpcs:implementation_26_1_2"))
    implementation(project(":plugins:twinpcs:implementation_1_21_11"))
    implementation(project(":plugins:twinpcs:implementation_1_21_9"))
    implementation(project(":plugins:twinpcs:implementation_1_21_6"))
    implementation(project(":plugins:twinpcs:implementation_1_21_5"))

    implementation(project(":libraries:common"))
    implementation(project(":libraries:jdb"))
    implementation(project(":libraries:config"))
    compileOnly("org.lushplugins.chatcolorhandler:paper:8.1.1")
    implementation("de.oliver.FancyAnalytics:logger:0.0.10")
    implementation("org.incendo:cloud-core:2.0.0")
    implementation("org.incendo:cloud-paper:2.0.0-beta.16")
    implementation("org.incendo:cloud-annotations:2.0.0")
    annotationProcessor("org.incendo:cloud-annotations:2.0.0")
    implementation("org.mineskin:java-client-jsoup:3.0.3-SNAPSHOT")

    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.intellectualsites.plotsquared:plotsquared-core:7.5.13")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.3")
    compileOnly("net.citizensnpcs:citizens-main:2.0.42-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
}

paper {
    name = "TwiNpcs"
    main = "de.oliver.fancynpcs.FancyNpcs"
    bootstrapper = "de.oliver.fancynpcs.loaders.FancyNpcsBootstrapper"
    loader = "de.oliver.fancynpcs.loaders.FancyNpcsLoader"
    foliaSupported = true
    version = getFNVersion()
    description = "Professional NPC plugin forked from FancyNpcs, with BetterModel, ModelEngine, MythicMobs and Bedrock viewer support"
    authors = listOf("OliverSchlueter", "siberanka")
    provides = listOf("FancyNpcs")
    apiVersion = "1.19"
    serverDependencies {
        register("PlaceholderAPI") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("MiniPlaceholders") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("PlotSquared") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("Citizens") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("BetterModel") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("ModelEngine") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("MythicMobs") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("floodgate") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("Geyser-Spigot") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }
}

tasks {
    runServer {
        minecraftVersion("26.2")
        //serverJar(file("/Users/oliver/Workspace/paper/paper-server/build/libs/paper-bundler-26.2.build.1-alpha.jar"))


        downloadPlugins {
//            url("https://fancyspaces.net/api/v1/spaces/s1gGcHj5/versions/A364LHvu/files/FancyWorlds-0.0.4.jar")
//            modrinth("FancyHolograms", "2.9.1")
//            modrinth("FancyDialogs", "1.1.2")
//            modrinth("FancyEconomy", "1.0.3+6")

//            modrinth("kite", "1.4.0")
//            hangar("PlaceholderAPI", "2.11.6")
//            hangar("ViaVersion", "5.8.1")
//            hangar("ViaBackwards", "5.8.1")
//            url("https://ci.citizensnpcs.co/job/citizens2/4138/artifact/dist/target/Citizens-2.0.41-b4138.jar")
        }
    }

    shadowJar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        relocate("org.incendo", "de.oliver")
        relocate("org.lushplugins.chatcolorhandler", "de.oliver.fancynpcs.libs.chatcolorhandler")
        archiveClassifier.set("")
        archiveBaseName.set("TwiNpcs")
        dependsOn(":plugins:twinpcs:twinpcs-api:shadowJar")
    }

    publishing {
        repositories {
            maven {
                name = "fancyinnovationsReleases"
                url = uri("https://repo.fancyinnovations.com/releases")
                credentials(PasswordCredentials::class)
                authentication {
                    isAllowInsecureProtocol = true
                    create<BasicAuthentication>("basic")
                }
            }

            maven {
                name = "fancyinnovationsSnapshots"
                url = uri("https://repo.fancyinnovations.com/snapshots")
                credentials(PasswordCredentials::class)
                authentication {
                    isAllowInsecureProtocol = true
                    create<BasicAuthentication>("basic")
                }
            }
        }
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
                from(project.components["java"])
            }
        }
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        options.release = 25
        // For cloud-annotations, see https://cloud.incendo.org/annotations/#command-components
        options.compilerArgs.add("-parameters")
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name() // We want UTF-8 for everything

        val props = mapOf(
            "description" to project.description,
            "version" to getFNVersion(),
            "commit_hash" to gitCommitHash.get(),
            "channel" to (System.getenv("RELEASE_CHANNEL") ?: "").ifEmpty { "undefined" },
            "platform" to (System.getenv("RELEASE_PLATFORM") ?: "").ifEmpty { "undefined" }
        )

        inputs.properties(props)

        filesMatching("version.yml") {
            expand(props)
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

val gitCommitHash: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }

val gitCommitMessage: Provider<String> = providers.exec {
    commandLine("git", "log", "-1", "--pretty=%B")
}.standardOutput.asText.map { it.trim() }

fun getFNVersion(): String {
    return file("VERSION").readText().trim()
}
