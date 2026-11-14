plugins {
    id("musicplayer.kotlin.jvm")
}

dependencies {
    testImplementation(testFixtures(projects.core.model))
    // Song and ArtistId come from :core:model, which also brings coroutines (Flow in the settings port).
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
}
