// DESIGN.md §3: core/ is pure Java. No net.minecraft.* / org.bukkit.* here,
// ever — this is what lets the Fabric mod and the Paper plugin share one
// protocol implementation instead of two that can drift apart.

plugins {
    id("java-library")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

// Second, explicit guard for the clean-room rule (on top of the fact that
// core has no Minecraft/Bukkit dependency at all, so such an import simply
// fails to compile today). This keeps failing loudly even if someone later
// adds an unrelated compileOnly dependency that happens to carry those
// packages.
val checkCleanRoom by tasks.registering {
    group = "verification"
    description = "Fails the build if core/ imports net.minecraft.* or org.bukkit.*"

    val sourceDirs = listOf(
        sourceSets["main"].allJava.srcDirs,
        sourceSets["test"].allJava.srcDirs
    ).flatten()

    doLast {
        val forbidden = listOf("import net.minecraft.", "import org.bukkit.")
        val offenders = mutableListOf<String>()
        sourceDirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .forEach { file ->
                        file.readLines().forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            if (forbidden.any { trimmed.startsWith(it) }) {
                                offenders += "${file.relativeTo(projectDir)}:${index + 1}: $trimmed"
                            }
                        }
                    }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "core/ must stay pure Java (no net.minecraft.* / org.bukkit.* imports). " +
                    "Offending lines:\n" + offenders.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkCleanRoom)
}
