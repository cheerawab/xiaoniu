plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("redis.clients:jedis:5.0.0")
}

shadowJar {
    archiveBaseName.set("McCommon")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("")
}
