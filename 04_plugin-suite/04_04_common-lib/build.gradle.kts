plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.redis:lettuce-core:6.3.2.RELEASE")
    implementation("com.mysql:mysql-connector-j:8.3.0")
}

shadowJar {
    archiveBaseName.set("McCommon")
    archiveVersion.set("1.0.0")
}
