plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    compileOnly(project(":04_plugin-suite:04_04_common-lib"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

shadowJar {
    archiveBaseName.set("McMatchmaking")
    archiveVersion.set("1.0.0")
}
