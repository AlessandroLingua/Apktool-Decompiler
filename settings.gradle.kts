plugins {
    // Allows Gradle to auto-download the JDK requested via -PtestJdkVersion.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "apktool-cli"
include(
    "brut.j.common", "brut.j.util", "brut.j.xml", "brut.j.yaml", "brut.j.zip",
    "brut.apktool:apktool-lib", "brut.apktool:apktool-cli", "brut.apktool:apktool-gui"
)

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {}
    }
}
