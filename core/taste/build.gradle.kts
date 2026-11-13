plugins {
    id("musicplayer.kotlin.jvm")
}

dependencies {
    testImplementation(testFixtures(projects.core.model))
    // Public types here (DynamicListProvider, SongRef) come from :core:model.
    api(projects.core.model)
}
