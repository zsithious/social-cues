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

// P6 (config UI) deps, filled in for all twelve rows by P7. Neither project's
// version number tracks Minecraft's in a form you can compute — ModMenu 17.0.0
// is 1.21.11's while its Maven "latest" was a newer Minecraft's — so each row
// was looked up against the Modrinth API's `game_versions` filter, exactly like
// the fabricApi column, taking the newest `release` (never a beta: filtering by
// game version alone would have pinned 1.21.11 to ModMenu 17.0.1-beta.1). The
// method is self-checking — re-running it on 1.21.11 reproduces the 17.0.0 /
// 21.11.153 pair P6 had already found by hand.
//
// Still nullable rather than required: absent is silence, not a default. A
// guessed version for a row nobody has built against would resolve and then
// fail in ways that look like our bug.
val modMenuVersion = row["modMenu"] as String?
val clothConfigVersion = row["clothConfig"] as String?
// Cloth's own library split: me.shedaniel.math.Rectangle and friends live in a
// separate mod (cloth-basic-math), which Cloth ships as a nested jar and also
// declares as a POM dependency. See the dependency block below for why this
// row has to name it explicitly instead of inheriting it. P7 read the value
// for every row out of that row's own cloth-config-fabric POM rather than
// copying 1.21.11's downward; all seven distinct Cloth releases the twelve
// rows use happen to declare basic-math 0.6.1, but that is an observation, not
// a rule the next Cloth release is bound by.
val basicMathVersion = row["basicMath"] as String?

// The three columns are one feature, so they are pinned together or not at all.
// integrations/configui/ contains both the Cloth screen and the ModMenu
// entrypoint that opens it, and it is compiled as a unit — pinning only some
// of them would fail at compile time anyway, just with a stack of "cannot
// find symbol" lines instead of this sentence.
require((modMenuVersion == null) == (clothConfigVersion == null)
        && (clothConfigVersion == null) == (basicMathVersion == null)) {
    "versions.json row for $mcVersion pins only some of modMenu/clothConfig/basicMath " +
        "(modMenu=$modMenuVersion, clothConfig=$clothConfigVersion, basicMath=$basicMathVersion). " +
        "The P6 config UI needs all three or none — see integrations/configui/'s package-info."
}

/**
 * DESIGN.md §14 P8 — the Simple Voice Chat API floor, deliberately not a
 * versions.json column: the artifact is Minecraft-independent, so all twelve
 * rows share one value. See integrations/voicechat/'s package-info.
 */
val voicechatApiVersion = "2.6.0"

/** DESIGN.md §14 P6: does this row build the config UI at all? See integrations/configui/'s package-info. */
val configUiEnabled = clothConfigVersion != null
val loomVersionForRow = row["loom"] as String
val bucket = row["bucket"] as String
val loaderVersion = providers.gradleProperty("loader_version").get()

// DESIGN.md §7 "P7 uygulama notu" — the second version axis. `bucket` groups
// rows by *render* generation; `compat` groups them by the seams that fall
// somewhere else, including two that sit in mc-shared, which has no bucket at
// all. Measured, not assumed: the keyboard-event seam is at 1.21.9 and the
// render-layer rename is at 1.21.11, so these are genuinely not the bucket
// boundaries. See adapters/compat/'s package-info.java.
val compat = row["compat"] as String
require(rootProject.file("adapters/compat/$compat").isDirectory) {
    "versions.json row for $mcVersion names compat generation '$compat', but " +
        "adapters/compat/$compat/ does not exist. Every row must name one of the " +
        "directories under adapters/compat/ — see that package's package-info.java."
}

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
    // P6 config UI. Neither library is on Maven Central; both publish only to
    // their own maven. Scoped with content filters so a typo in any *other*
    // coordinate cannot silently start resolving from a third-party host —
    // these two repos are trusted for exactly two groups and nothing else.
    maven("https://maven.terraformersmc.com/releases/") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
    maven("https://maven.shedaniel.me/") {
        name = "Shedaniel"
        content {
            includeGroup("me.shedaniel.cloth")
            includeGroup("me.shedaniel.cloth.api")
        }
    }
    // P8 voice integration. Same reasoning as the two above: scoped to the one
    // group it is trusted for.
    maven("https://maven.maxhenkel.de/repository/public") {
        name = "MaxHenkel"
        content { includeGroup("de.maxhenkel.voicechat") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":core"))
    include(project(":core"))

    // ModMenu is a SOFT dependency: compile-only, so the mod can implement
    // ModMenuApi without ever requiring ModMenu to be installed (the
    // entrypoint is simply never called when it is absent — that is how
    // Fabric entrypoints work, an absent entrypoint owner is not an error).
    // modLocalRuntime puts it in the dev runs anyway, because a config-screen
    // hook you cannot click during a hand test is a hook you cannot verify.
    // Never `include` it: bundling a mod-list UI into a cue mod would be
    // hostile, and the user may deliberately not want it.
    if (modMenuVersion != null) {
        modCompileOnly("com.terraformersmc:modmenu:$modMenuVersion")
        modLocalRuntime("com.terraformersmc:modmenu:$modMenuVersion")
    }

    // DESIGN.md §6 "Konuşma" / §14 P8. Simple Voice Chat is a SOFT dependency
    // reached only through the `voicechat` entrypoint, which nothing but that
    // mod ever reads — so `compileOnly` (not modCompileOnly: the API artifact
    // is a plain library jar with no fabric.mod.json and no Minecraft types in
    // it, so there is nothing for loom to remap).
    //
    // Never `include`d, and this is the one dependency here where that is a
    // licence statement rather than a packaging preference: Simple Voice Chat
    // is All Rights Reserved, API module included. compileOnly is what makes
    // "no third-party ARR code ships in this MIT project" a mechanical fact.
    //
    // Pinned to the LOWEST version that carries what we call, not the newest:
    // VoicechatClientApi#isTalking() first appears in 2.6.0 (measured by
    // reading the published API jars 2.4.0 -> 2.6.20). Compiling against the
    // floor maximises the range of installed Simple Voice Chat builds that
    // link at runtime; anything older is caught and disabled at the call site
    // (ClientCueCapture#probeTransmitting) rather than crashing.
    //
    // Unconditional across all twelve rows, unlike the config UI above: the
    // API jar contains no net/minecraft reference at all, so one artifact is
    // correct for every Minecraft version and no versions.json column is
    // needed. See integrations/voicechat/'s package-info.
    compileOnly("de.maxhenkel.voicechat:voicechat-api:$voicechatApiVersion")

    // Cloth Config is a HARD dependency of the config screen, so it is a real
    // implementation dependency, and `include`d (jar-in-jar) so an end user
    // needs nothing beyond Fabric API. Cloth supports and documents JiJ, and
    // the loader de-duplicates nested copies by version, so a user who
    // already has it standalone keeps whichever is newer.
    //
    // `transitive = false` on both: Cloth's POM drags in its own Fabric API
    // and Minecraft coordinates, which would fight the versions this row
    // already pinned above. We want the library, not its idea of the platform.
    if (clothConfigVersion != null) {
        modImplementation("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") { isTransitive = false }
        include("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") { isTransitive = false }

        // cloth-basic-math is the one transitive dependency `transitive = false`
        // must NOT have cut. Cloth's public API is typed in terms of
        // me.shedaniel.math.Rectangle (AbstractConfigEntry and the widget
        // classes underneath it), so a Cloth on the classpath without it is a
        // Cloth whose screen constructor dies with NoClassDefFoundError the
        // moment it is opened. P6 hand test, 2026-08-10: ModMenu caught exactly
        // that and greyed the button out with "The 'socialcues' mod config
        // screen is not available because me/shedaniel/math/Rectangle is
        // missing" — compilation had been perfectly happy, because our own
        // source never names the class.
        //
        // Only `modImplementation`, deliberately not `include`: the *shipped*
        // jar already carries it, because Cloth's own jar nests
        // META-INF/jars/basic-math-<v>.jar and declares it in its `jars` list
        // (Fabric resolves nested jars recursively). It is the dev classpath —
        // which is built from the Gradle graph, not from nested jars — that
        // this line exists to repair. Including it again would put a second
        // copy of the same mod id in our jar for the loader to de-duplicate.
        modImplementation("me.shedaniel.cloth:basic-math:$basicMathVersion") { isTransitive = false }
    }
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
        // ...and both of them turn the mod's own FINE logging on (see
        // hand-test-logging.properties for why: ClientCueCapture.logTransition
        // is the only line that says what a client actually decided to send,
        // and at the default INFO level it is invisible — which has now cost
        // two separate hand-test rounds).
        val julConfig = rootProject.file("mc/hand-test-logging.properties").absolutePath
        // The two hand-test clients have fixed names so a server can tell them
        // apart, but a name is sometimes the point of the test -- joining a real
        // server that already knows an account, or shooting footage where
        // "SocialCuesA" would be the only thing on screen that is not real.
        // -PclientAName=<name> / -PclientBName=<name> override just the name;
        // everything else about the run configuration is unchanged.
        val clientAName = (project.findProperty("clientAName") as String?) ?: "SocialCuesA"
        val clientBName = (project.findProperty("clientBName") as String?) ?: "SocialCuesB"
        create("clientA") {
            client()
            configName = "Client A ($clientAName)"
            runDir = "run-a"
            programArgs("--username", clientAName, "--width", "854", "--height", "480")
            vmArgs("-Xmx1G", "-XX:+UseG1GC", "-XX:ParallelGCThreads=2", "-XX:ConcGCThreads=1",
                    "-Djava.util.logging.config.file=$julConfig")
        }
        create("clientB") {
            client()
            configName = "Client B ($clientBName)"
            runDir = "run-b"
            programArgs("--username", clientBName, "--width", "854", "--height", "480")
            vmArgs("-Xmx1G", "-XX:+UseG1GC", "-XX:ParallelGCThreads=2", "-XX:ConcGCThreads=1",
                    "-Djava.util.logging.config.file=$julConfig")
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(rootProject.file("mc-shared/src/main/java"))
            srcDir(rootProject.file("adapters/$bucketDirName/src/main/java"))
            // The compat layer (DESIGN.md §7 P7). Deliberately added *before*
            // nothing and after nothing in particular — all three source dirs
            // compile as one unit, which is exactly what lets mc-shared call
            // adapter.compat classes without any project dependency: the
            // classes are simply on the same javac invocation. Only ever one
            // compat generation is on the path, so the fixed class names
            // adapter.compat.* resolve unambiguously.
            srcDir(rootProject.file("adapters/compat/$compat/src/main/java"))
            // DESIGN.md §14 P6. Unlike the buckets this directory is not
            // version-specific — Cloth's builder API is stable across 1.21.x —
            // it is *dependency*-specific: it is the only source that imports
            // Cloth/ModMenu, so it can only be compiled by a row that pins
            // them. P7 turns this on for the other eleven rows by filling in
            // their versions.json columns, not by forking the source.
            if (configUiEnabled) {
                srcDir(rootProject.file("integrations/configui/src/main/java"))
            }
            // DESIGN.md §14 P8. Unconditional, unlike configui above: the voice
            // chat API is Minecraft-independent, so every row can compile it.
            srcDir(rootProject.file("integrations/voicechat/src/main/java"))
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

// fabric.mod.json is one shared file for all twelve rows (mc-shared/src/main/
// resources), but the P6 config UI only exists on rows that pin Cloth/ModMenu.
// An entrypoint naming a class the jar does not contain is a hard crash at
// load, so the two affected lists — and the Cloth dependency declaration that
// goes with the bundled copy — are filled in per row here rather than written
// once in the file. Same shape of problem the per-bucket socialcues.mixins.json
// already had to solve in P4 (DESIGN.md §7), same solution: let the build
// decide, never the shared file.
val clientEntrypoints = buildString {
    append("\"dev.zsithious.socialcues.mcshared.SocialCuesClientInitializer\"")
    if (configUiEnabled) {
        append(", \"dev.zsithious.socialcues.configui.ConfigUiClientEntrypoint\"")
    }
}
val modMenuEntrypoints = if (configUiEnabled) "\"dev.zsithious.socialcues.configui.SocialCuesModMenu\"" else ""
// Declared even though Cloth is bundled (jar-in-jar): the loader de-duplicates
// nested copies across mods and keeps one, so what actually loads may be some
// other mod's older Cloth. A `depends` line is what turns that into a clear
// startup message instead of a NoSuchMethodError in our screen. Major version
// only — Cloth's major tracks the Minecraft generation it targets.
val extraDepends = if (configUiEnabled) {
    ",\n    \"cloth-config\": \">=${clothConfigVersion!!.substringBefore('.')}\""
} else {
    ""
}

// DESIGN.md §13 (P8). Built here rather than written into the shared
// fabric.mod.json because the block has to *disappear* when the URLs are not
// set yet — an empty string, or worse a placeholder, in published mod metadata
// is a dead link on every mirror that copies it. See gradle.properties.
val contactBlock = buildString {
    val entries = listOfNotNull(
        providers.gradleProperty("project_homepage").orNull?.takeIf { it.isNotBlank() }
            ?.let { "\"homepage\": \"$it\"" },
        providers.gradleProperty("project_sources").orNull?.takeIf { it.isNotBlank() }
            ?.let { "\"sources\": \"$it\"" },
        providers.gradleProperty("project_issues").orNull?.takeIf { it.isNotBlank() }
            ?.let { "\"issues\": \"$it\"" }
    )
    if (entries.isNotEmpty()) {
        append("\n  \"contact\": {\n    ")
        append(entries.joinToString(",\n    "))
        append("\n  },")
    }
}

tasks.processResources {
    inputs.property("contactBlock", contactBlock)
    inputs.property("version", project.version)
    inputs.property("mcVersionRange", "~$mcVersion")
    inputs.property("clientEntrypoints", clientEntrypoints)
    inputs.property("modMenuEntrypoints", modMenuEntrypoints)
    inputs.property("extraDepends", extraDepends)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "mc_version_range" to "~$mcVersion",
            "client_entrypoints" to clientEntrypoints,
            "modmenu_entrypoints" to modMenuEntrypoints,
            "extra_depends" to extraDepends,
            "contact_block" to contactBlock
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

    val sourceDirs = listOfNotNull(
        rootProject.file("mc-shared/src"),
        rootProject.file("adapters/$bucketDirName/src"),
        // The compat layer is client source like any other, and is in fact the
        // one place a raw key event is handled at all — so it gets the same
        // guarantee, not an exemption (see TypingKeyEvents' privacy note).
        rootProject.file("adapters/compat/$compat/src"),
        // The config UI is client source like any other and gets the same
        // guarantee — a text field on a config screen is still a text field.
        rootProject.file("integrations/configui/src").takeIf { configUiEnabled },
        // The voice bridge is client source like any other. It reads no text
        // by construction (its whole surface is two booleans), and this is
        // what keeps that true of whatever it becomes later.
        rootProject.file("integrations/voicechat/src")
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
