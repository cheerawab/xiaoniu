plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "co.partygame"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public") }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    compileOnly("net.luckperms:api:5.4")
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
    }

    javadoc {
        options.encoding = "UTF-8"
    }
}
