plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "co.partygame"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven { url = uri("https://LuckPerms.dev/repo") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://mvn.lucko.me/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    flatDir {
        dirs("libs")
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    // compileOnly("co.partygame:common-lib:1.0.0")  // not yet published
    compileOnly("org.luckperms:LuckPerms-Bukkit:5.4.102")
    compileOnly("net.luckperms:api:5.4.102")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
}

tasks {
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveFileName.set("Matchmaking-${project.version}.jar")
        minimize()
    }
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-enablePreview")
    }
    javadoc {
        options.encoding = "UTF-8"
    }
}
