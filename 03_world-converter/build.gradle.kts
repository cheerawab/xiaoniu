plugins {
    id("java")
    id("application")
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
    maven { url = uri("https://repo.runelite.cc/") }
}

dependencies {
    // implementation("net.runelite:arpack:1.6.1")  // repo.runelite.cc is down
    // implementation("net.jimmc:jnbt:1.5.0")  // repo.runelite.cc is down
    implementation("com.google.guava:guava:32.1.1-jre")
}

application {
    mainClass = "co.partygame.converter.WorldConverter"
}

tasks {
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveFileName.set("world-converter-${project.version}.jar")
    }
    named("assemble") {
        dependsOn("shadowJar")
    }
    compileJava {
        options.encoding = "UTF-8"
    }
    javadoc {
        options.encoding = "UTF-8"
    }
}
