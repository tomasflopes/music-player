plugins {
    id("musicplayer.kotlin.jvm")
    application
}

application {
    applicationName = "analyser"
    mainClass.set("dev.tomasflopes.musicplayer.tools.analyser.MainKt")
}

// The generated MP3s and their reference are shared with :data's instrumented tests (ADR 0053).
sourceSets.named("test") {
    resources.srcDir(rootProject.file("testdata/analysis"))
}

dependencies {
    implementation(projects.core.analysis)
    testImplementation(testFixtures(projects.core.analysis))
    testImplementation(testFixtures(projects.core.model))
}
