plugins {
    id("musicplayer.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    testImplementation(testFixtures(projects.core.model))
    // The library audit groups and orders albums with the app's own rules (plan Task 6).
    testImplementation(projects.core.model)
}

// The sample music library used on the emulator: generated silence with real tags, nothing downloaded.
val samplesDir = layout.buildDirectory.dir("samples")

val generateSamples by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes the generated sample MP3 library to build/samples."
    classpath = sourceSets["testFixtures"].runtimeClasspath
    mainClass = "dev.tomasflopes.musicplayer.core.tags.SampleLibraryGenerator"
    argumentProviders.add(CommandLineArgumentProvider { listOf(samplesDir.get().asFile.absolutePath) })
    outputs.dir(samplesDir)
    doFirst { delete(samplesDir) }
}

// ADR 0006, validation layer 3: run the reader over the owner's real library on the Mac. Read-only.
// Usage: ./gradlew :core:tags:auditLibrary -Proot="/path/to/music" [-Palbums="DAMN.,LOUD."]
val auditLibrary by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Reads the tags of every MP3 under -Proot=<music folder> and writes build/tag-audit."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.tomasflopes.musicplayer.core.tags.audit.LibraryAudit"
    val root = providers.gradleProperty("root")
    val albums = providers.gradleProperty("albums").orElse("")
    val auditDir = layout.buildDirectory.dir("tag-audit")
    argumentProviders.add(
        CommandLineArgumentProvider { listOf(root.get(), auditDir.get().asFile.absolutePath, albums.get()) },
    )
    outputs.upToDateWhen { false }
}
