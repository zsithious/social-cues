// Root build script. No plugins applied here — every subproject configures
// its own build logic. This block only pins group/version and the default
// repository for all subprojects so it is not repeated 12+ times.

// The Gradle daemon's own JDK is what Loom and the Minecraft toolchain actually
// run on; `options.release.set(21)` in the subprojects controls the bytecode
// target, not this. Without a check here a mismatch surfaces much later as an
// opaque Loom or mixin failure, so fail early and say what to do about it.
//
// This refuses anything that is not 21 rather than only anything older, because
// the repo's rule is that version boundaries are measured, not guessed (see
// tools/seam.sh and tools/verify-mixins.py). Loom 1.17.17 has been run on 21 and
// on nothing else here; this machine's system JDK is 25 and keeping it away is
// exactly why an absolute java.home used to be pinned in gradle.properties.
// Anyone who wants to try another JDK can, but must say so out loud.
val runningJava = JavaVersion.current()
if (runningJava != JavaVersion.VERSION_21 && !project.hasProperty("allowUnverifiedJdk")) {
    throw GradleException(
        "Social Cues builds on JDK 21; the Gradle daemon is running on JDK $runningJava.\n" +
        "\n" +
        "  Fix:      set JAVA_HOME to a JDK 21 installation, or put\n" +
        "            org.gradle.java.home=/path/to/jdk21 in your own\n" +
        "            ~/.gradle/gradle.properties (machine-specific, so not committed here).\n" +
        "  Override: ./gradlew -PallowUnverifiedJdk ...  (untested; Loom 1.17.17 has only\n" +
        "            been verified on 21)"
    )
}

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
