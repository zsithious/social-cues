// DESIGN.md §8 — single Paper/Spigot/Purpur/Leaf jar for all of 1.21.x.
// No NMS/Paperweight: compiled against the oldest supported API
// (paper-api:1.21-R0.1-SNAPSHOT) and shipped as a classic plugin.yml.

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    // DESIGN.md §14 P8. Scoped with a content filter for the same reason the
    // Fabric side scopes its two: this host is trusted for one group, and a
    // typo in any other coordinate must not silently resolve from it.
    maven("https://repo.extendedclip.com/releases/") {
        name = "ExtendedClip"
        content { includeGroup("me.clip") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation(project(":core"))

    // DESIGN.md §14 P8 — PlaceholderAPI is a SOFT dependency (plugin.yml's
    // `softdepend`), so compileOnly: the server operator supplies their own
    // copy, exactly as every expansion in that ecosystem does.
    //
    // Not shaded and never bundled, and here that is a licence statement as
    // much as a packaging one: PlaceholderAPI is GPL-3.0 and this project is
    // MIT. compileOnly is what keeps the two from ever meeting inside a jar
    // we publish.
    //
    // Pinned low on purpose (same rule as the voice API on the Fabric side):
    // 2.11.6 predates every 1.21 server, and PlaceholderExpansion's surface
    // has been stable for far longer, so compiling against the floor is what
    // makes the widest range of installed PlaceholderAPI builds link at
    // runtime. Anything that still does not is caught in
    // PlaceholderIntegration rather than taking the plugin down.
    compileOnly("me.clip:placeholderapi:2.11.6")
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

// DESIGN.md §13 (P8) — same rule as the Fabric side's `contact` block: the
// line disappears entirely when no URL is configured, so a placeholder can
// never reach a published plugin.yml. See gradle.properties.
val websiteLine = providers.gradleProperty("project_homepage").orNull
    ?.takeIf { it.isNotBlank() }
    ?.let { "website: $it\n" }
    ?: ""

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("websiteLine", websiteLine)
    filesMatching("plugin.yml") {
        expand(
            "version" to project.version,
            "website_line" to websiteLine
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
