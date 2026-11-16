plugins {
    application
}

dependencies {
    // apktool-cli brings in apktool-lib and the apktool.properties resource (version info).
    implementation(project(":brut.apktool:apktool-lib"))
    implementation(project(":brut.apktool:apktool-cli"))
}

application {
    mainClass.set("brut.apktool.gui.ApktoolGui")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register("shadowJar", Jar::class) {
    dependsOn("build")

    group = "build"
    description = "Creates a single executable JAR of the GUI with all dependencies"
    archiveFileName.set("apktool-gui-all.jar")
    manifest.attributes["Main-Class"] = "brut.apktool.gui.ApktoolGui"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    from(configurations.runtimeClasspath.get().map(::zipTree))
    with(tasks.jar.get())
}
