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
    implementation("net.runelite:arpack:1.6.1")
    implementation("net.jimmc:jnbt:1.5.0")
    implementation("com.google.guava:guava:32.1.1-jre")
}

application {
    mainClass = "co.partygame.converter.WorldConverter"
}

shadowJar {
    archiveFileName.set("world-converter-${project.version}.jar")
    minimize()
}

assemble {
    dependsOn(shadowJar)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    javadoc {
        options.encoding = "UTF-8"
    }
}
