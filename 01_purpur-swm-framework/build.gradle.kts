// import fr.milkhalli.spigot.slimeworldmanager.api.SlimeWorldManager  // haxey mirror is down - needs manual SWM API

plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "1.7.1"
}

group = "co.partygame.framework"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    
    // SlimeWorldManager API - needs manual addition if haxey mirror is back online
    // maven("https://maven.haxey.me/repository/slimeworldmanager")
}

dependencies {
    devBundle("purpur", "1.21.7")

    // SlimeWorldManager API  // haxey mirror is down
    // implementation("fr.milkhalli.spigot:SlimeWorldManager-API:3.0.11") {
    //     exclude(group = "org.spigotmc")
    // }

    // Folia / Paper API (Folia compatibility)
    compileOnly("dev.folia:folia-api:1.21.4-R0.1-SNAPSHOT")

    // BungeeCord messaging channels
    compileOnly("net.md-5:bungeecord-chat:1.20-R0.1")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(project.properties)
        }
    }

    jar {
        manifest {
            attributes(
                "Main-Class" to "co.partygame.framework.SwmPurpurPlugin",
                "PaperWeight-Remap-File-Path" to "build/paperweight/paperweight-userdev.jar"
            )
        }
    }

    // reinstateJar {  // paperweight task - temporarily disabled
    //     // Remove Paper remapped classes from final jar
    // }
}
