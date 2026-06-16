plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "1.7.6"
}

group = "co.partygame.partygame"
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
}

dependencies {
    paperweight.paperDevBundle("1.21.7-R0.1-SNAPSHOT")

    // Project dependency — this framework depends on the SWM framework (module 1)
    implementation(project(":01_purpur-swm-framework"))

    // BungeeCord messaging for cross-server matchmaking
    compileOnly("net.md-5:bungeecord-chat:1.20-R0.1")
    compileOnly("net.md-5:bungeecord-api:1.20-R0.1")

    // JSON/MessagePack for match payload processing
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
                "Main-Class" to "co.partygame.partygame.PartyGamePlugin"
            )
        }
    }
}
