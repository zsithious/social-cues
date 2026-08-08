// Shared build script for every generated :mc:<version> project.
// settings.gradle.kts points ALL :mc:<version> projects at this single file
// (buildFileName = "../mc.gradle.kts") so there is exactly one Fabric/Loom
// build definition, not twelve hand-maintained copies.
//
// Each project resolves its own row from versions.json using its own
// project name (which settings.gradle.kts set to the mc version string,
// e.g. "1.21.11").

import groovy.json.JsonSlurper

plugins {
    id("fabric-loom")
}

@Suppress("UNCHECKED_CAST")
val versionsData = JsonSlurper().parse(rootProject.file("versions.json")) as Map<String, Any?>
val versionRows = versionsData["versions"] as List<Map<String, Any?>>

val mcVersion = project.name
val row = versionRows.first { it["mc"] == mcVersion }

val yarnMappings = row["yarn"] as String
val fabricApiVersion = row["fabricApi"] as String
val loomVersionForRow = row["loom"] as String
val bucket = row["bucket"] as String
val loaderVersion = providers.gradleProperty("loader_version").get()

val bucketDirName = "bucket$bucket"

// mc.gradle.kts documents the loom version each row asked for; a real
// multi-loom-version split (see DESIGN.md §11) is a P7 concern, not P0 —
// for now all 12 rows request 1.17.17, so this is just an assertion.
val actualLoomVersion = "1.17.17"
require(loomVersionForRow == actualLoomVersion) {
    "versions.json row for $mcVersion asks for loom $loomVersionForRow " +
        "but this build only applies fabric-loom $actualLoomVersion"
}

base {
    archivesName.set("socialcues-fabric-$mcVersion")
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":core"))
    include(project(":core"))
}

sourceSets {
    main {
        java {
            srcDir(rootProject.file("mc-shared/src/main/java"))
            srcDir(rootProject.file("adapters/$bucketDirName/src/main/java"))
        }
        resources {
            srcDir(rootProject.file("mc-shared/src/main/resources"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcVersionRange", "~$mcVersion")
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "mc_version_range" to "~$mcVersion"
        )
    }
}
