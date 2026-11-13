plugins {
    id("musicplayer.kotlin.jvm")
    `java-test-fixtures`
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
