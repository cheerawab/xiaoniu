plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    compileOnly(project(":01_purpur-swm-framework"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.slslimevs:SlimeWorldManager:5.2.1")
}

shadowJar {
    archiveBaseName.set("PartyGameCore")
    archiveVersion.set("1.0.0")
}
