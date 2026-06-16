plugins {
    id("java")
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
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://api.papermc.io/v2/maven") }
    flatDir {
        dirs("libs")
    }
}

dependencies {
    compileOnly(files("../01_purpur-swm-framework/libs/paper-api-1.21.7-R0.1-SNAPSHOT.jar"))
    compileOnly(files("../01_purpur-swm-framework/libs/brigadier-1.0.16.jar"))

    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    // Project dependency: this framework depends on the SWM framework (module 1)
    implementation(project(":01_purpur-swm-framework"))

    // BungeeCord messaging for cross-server matchmaking
    // compileOnly("net.md-5:bungeecord-chat:1.20-R0.1")
    // compileOnly("net.md-5:bungeecord-api:1.20-R0.1")

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
