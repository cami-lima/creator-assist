plugins {
    // Lets Gradle automatically download a JDK 17 if the machine
    // running this doesn't already have one discoverable. Without
    // this, the java/kotlin toolchain settings in build.gradle.kts
    // only work if a compatible JDK already happens to be installed.
    // This is what makes "clean environment, whatever JDK you have" a
    // real guarantee instead of something that only worked because
    // IntelliJ happened to download one for us during development.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "creator-assist"