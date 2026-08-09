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

loom {
    runs {
        // Two named client runs so a single machine can put two players on one
        // server at once — the only way to hand-test Layer 1/2 at all, since a
        // cue is by definition something *another* player's client renders.
        // Separate runDirs (not just usernames) because two clients sharing one
        // game directory fight over options.txt and the log file; separate
        // usernames because a server refuses a second login with a name that is
        // already online. Requires an offline-mode server (~/social-cues-testserver,
        // 127.0.0.1:25565; galactic and its Velocity proxy also run online-mode=false).
        //
        // Both clients are deliberately kept small: two Minecraft instances plus a
        // server share this machine with whatever else is running on it. 1G heap is
        // ample at render distance 2 on a superflat world, the GC thread counts stop
        // two JVMs from between them claiming every core, and the 854x480 window is
        // both cheaper to draw and small enough to put the two side by side (which
        // is how a cue gets watched: you act on one, you read the icon on the other).
        // The matching low graphics settings live in each runDir's own options.txt.
        create("clientA") {
            client()
            configName = "Client A (SocialCuesA)"
            runDir = "run-a"
            programArgs("--username", "SocialCuesA", "--width", "854", "--height", "480")
            vmArgs("-Xmx1G", "-XX:+UseG1GC", "-XX:ParallelGCThreads=2", "-XX:ConcGCThreads=1")
        }
        create("clientB") {
            client()
            configName = "Client B (SocialCuesB)"
            runDir = "run-b"
            programArgs("--username", "SocialCuesB", "--width", "854", "--height", "480")
            vmArgs("-Xmx1G", "-XX:+UseG1GC", "-XX:ParallelGCThreads=2", "-XX:ConcGCThreads=1")
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(rootProject.file("mc-shared/src/main/java"))
            srcDir(rootProject.file("adapters/$bucketDirName/src/main/java"))
        }
        resources {
            srcDir(rootProject.file("mc-shared/src/main/resources"))
            // DESIGN.md §7 P4b / §3.1: each bucket ships its own socialcues.mixins.json
            // under its own resources dir. The
            // shared fabric.mod.json references the mixin config by a single fixed name
            // ("socialcues.mixins.json"); only ever one bucket's resources dir is ever on
            // a given :mc:<version> project's classpath, so the name is never ambiguous.
            srcDir(rootProject.file("adapters/$bucketDirName/src/main/resources"))
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

// DESIGN.md §10.1 / P3 privacy fix: written chat/sign/book text must never
// be read, not even transiently to peek at a single character (a heap dump,
// a later log line, or a careless refactor can leak it right back out — the
// rule is "never read", not "read then discard"). Mirrors core/build.gradle.kts's
// checkCleanRoom: a documentation comment saying "this never reads the
// field" is not a guarantee, a build failure is. See
// mcshared.client.ClientCueCapture's class Javadoc for the keycode-only
// approach this enforces.
val checkNoTextAccess by tasks.registering {
    group = "verification"
    description = "Fails the build if mc-shared/adapter source reads chat/sign/book message content."

    val sourceDirs = listOf(
        rootProject.file("mc-shared/src"),
        rootProject.file("adapters/$bucketDirName/src")
    )

    doLast {
        // Deliberately broad: "getText(" and "getMessage(" catch any
        // text-content accessor, not just ChatScreen's, because the same
        // guarantee applies to sign lines and book pages too.
        val forbidden = listOf("getText(", "getMessage(", "chatField", "originalChatText")
        val offenders = mutableListOf<String>()
        sourceDirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .forEach { file ->
                        file.readLines().forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            // Javadoc/line-comment lines are allowed to *mention*
                            // these names when explaining why they're forbidden
                            // (see ClientCueCapture's class Javadoc) — only actual
                            // code lines are policed, matching this project's
                            // Javadoc convention of prefixing every continuation
                            // line with "*".
                            val isCommentLine = trimmed.startsWith("*") || trimmed.startsWith("//")
                            if (!isCommentLine && forbidden.any { trimmed.contains(it) }) {
                                offenders += "${file.relativeTo(rootProject.projectDir)}:${index + 1}: $trimmed"
                            }
                        }
                    }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "mc-shared/adapters must never read chat/sign/book message content " +
                    "(DESIGN.md §10.1). Offending lines:\n" + offenders.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkNoTextAccess)
}
