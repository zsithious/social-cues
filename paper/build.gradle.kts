// DESIGN.md §8 — single Paper/Spigot/Purpur/Leaf jar for all of 1.21.x.
// No NMS/Paperweight: compiled against the oldest supported API
// (paper-api:1.21-R0.1-SNAPSHOT) and shipped as a classic plugin.yml.

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation(project(":core"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

base {
    archivesName.set("socialcues-paper")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
