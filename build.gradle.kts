// Root build script. No plugins applied here — every subproject configures
// its own build logic. This block only pins group/version and the default
// repository for all subprojects so it is not repeated 12+ times.

allprojects {
    group = project.property("maven_group") as String
    version = project.property("mod_version") as String

    repositories {
        mavenCentral()
    }
}

// DESIGN.md §14 P7 acceptance criterion: one command that produces every
// shippable artifact — the twelve Fabric jars plus the single Paper jar
// (DESIGN.md §8: one plugin jar covers all of 1.21.x, so it is one entry here,
// not twelve).
//
// The version list is read from versions.json rather than hardcoded, for the
// same reason settings.gradle.kts reads it: adding a Minecraft version must be
// a one-line data change, never an edit to build logic. settings.gradle.kts has
// already turned each of those rows into a real :mc:<version> project by the
// time this runs.
//
// Note this task is exactly what gradle.properties' configureondemand exists to
// protect the *rest* of the build from: depending on all twelve rows configures
// all twelve, which means twelve Loom provisioning passes. That cost is
// inherent to "build everything" and is why :core:test and single-row builds
// are the everyday commands instead.
val buildAll by tasks.registering {
    group = "build"
    description = "Builds every :mc:<version> Fabric jar and the Paper plugin jar."

    @Suppress("UNCHECKED_CAST")
    val versionsData = groovy.json.JsonSlurper().parse(file("versions.json")) as Map<String, Any?>
    val versionRows = versionsData["versions"] as List<Map<String, Any?>>
    versionRows.forEach { row ->
        dependsOn(":mc:${row["mc"]}:build")
    }
    dependsOn(":paper:build")
}
