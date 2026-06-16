plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "co.partygame"
version = "1.0.0"

repositories {
    mavenCentral()
    flatDir {
        dirs("libs")
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    compileOnly(files("../../01_purpur-swm-framework/libs/paper-api-1.21.7-R0.1-SNAPSHOT.jar"))
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    compileOnly("org.luckperms:LuckPerms-Bukkit:5.4.102")
    compileOnly("net.luckperms:api:5.4.102")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
}

tasks {
    shadowJar {
        archiveFileName.set("Matchmaking-${project.version}.jar")
    }

    jar {
        enabled = false
    }

    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-enable-preview")
    }

    javadoc {
        options.encoding = "UTF-8"
    }
}
