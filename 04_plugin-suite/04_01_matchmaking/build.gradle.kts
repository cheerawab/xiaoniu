plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    maven { url = uri("https://papermc.io/repo/repository/maven-releases/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-releases/") }
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

    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1")
    compileOnly("co.partygame:common-lib:1.0.0")
    compileOnly("org.luckperms:LuckPerms-Bukkit:5.4.102")
    compileOnly("net.luckperms:api:5.4.102")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
}

shadowJar {
    archiveFileName.set("Matchmaking-${project.version}.jar")
    minimize()
}

assemble {
    dependsOn(shadowJar)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-enablePreview")
    }
    javadoc {
        options.encoding = "UTF-8"
    }
}
