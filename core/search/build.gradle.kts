plugins {
    id("musicplayer.kotlin.jvm")
}

dependencies {
    testImplementation(testFixtures(projects.core.model))
    implementation(projects.core.model)
}
