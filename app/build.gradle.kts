plugins {
    id("musicplayer.android.application")
}

android {
    namespace = "dev.tomasflopes.musicplayer"
    defaultConfig {
        applicationId = "dev.tomasflopes.musicplayer"
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.taste)
    implementation(projects.core.search)
    implementation(projects.core.analysis)
    implementation(projects.data)
    implementation(projects.playback)
}
