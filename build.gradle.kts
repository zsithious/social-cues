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
