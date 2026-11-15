plugins {
    id("musicplayer.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    // The vectorizer returns a SoundSpace, which lives in the centre of the onion (ADR 0069).
    api(projects.core.model)
    // The batch runner suspends between songs (ADR 0059); core:model does the same for its ports.
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(testFixtures(projects.core.model))
}
