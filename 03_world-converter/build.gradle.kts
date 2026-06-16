plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

shadowJar {
    archiveBaseName.set("WorldConverter")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("all")
}
