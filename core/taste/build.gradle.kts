plugins {
    id("musicplayer.kotlin.jvm")
}

dependencies {
    // Public types here (DynamicListProvider, SongRef) come from :core:model.
    api(projects.core.model)
}
