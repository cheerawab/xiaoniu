plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
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
    flatDir {
        dirs("libs")
    }
}

dependencies {
    compileOnly(files("libs/paper-api-1.21.7-R0.1-SNAPSHOT.jar"))
    compileOnly(files("libs/brigadier-1.0.16.jar"))

    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    compileOnly("net.md-5:bungeecord-chat:1.20-R0.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
}

tasks {
    shadowJar {
        archiveFileName.set("SWMFramework-${project.version}.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    jar {
        enabled = true
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options.encoding = "UTF-8"
    }
}
