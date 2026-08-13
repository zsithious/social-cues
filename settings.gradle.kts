import groovy.json.JsonSlurper

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        // Centralized here (not in mc/mc.gradle.kts) because that one build
        // script is shared, verbatim, by all 12 generated :mc:<version>
        // projects — the plugins{} DSL only accepts a compile-time constant
        // version, not a value looked up from versions.json at
        // configuration time. All 12 rows currently pin loom "1.17.17"
        // (see versions.json); mc.gradle.kts asserts that at configuration
        // time so drift between the two is a loud failure, not silent.
        id("fabric-loom") version "1.17.17"
    }
}

rootProject.name = "social-cues"

include("core")
include("paper")

// mc-shared/ and adapters/bucket{A,B,C,D}/ are NOT separate Gradle projects.
// They are plain source directories that the generated :mc:<version>
// projects below absorb directly (java.srcDirs), because they never compile
// standalone — they only make sense against a specific Minecraft/mapping
// version. See mc/mc.gradle.kts.

// :mc:<version> projects are generated from versions.json instead of being
// hand-written 12 times. Every generated project shares one build script,
// mc/mc.gradle.kts, which reads versions.json again at configuration time
// to look up its own row (matched by project name == mc version).
@Suppress("UNCHECKED_CAST")
val versionsData = JsonSlurper().parse(file("versions.json")) as Map<String, Any?>
val versionRows = versionsData["versions"] as List<Map<String, Any?>>

versionRows.forEach { row ->
    val mcVersion = row["mc"] as String
    val path = ":mc:$mcVersion"
    val dir = file("mc/$mcVersion")

    // Nothing inside mc/<version>/ is tracked: the build output and the two
    // hand-test run dirs are all generated and gitignored, so these directories
    // simply do not exist in a fresh clone -- and Gradle refuses to configure a
    // project whose directory is missing, which failed every CI run until this
    // line existed. Creating them here keeps the rule that adding a Minecraft
    // version is a one-line edit to versions.json; the alternative, a committed
    // .gitkeep per row, would be a second list of versions free to drift from
    // the first.
    dir.mkdirs()

    include(path)
    project(path).projectDir = dir
    project(path).buildFileName = "../mc.gradle.kts"
}
