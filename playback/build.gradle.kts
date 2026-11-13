plugins {
    id("musicplayer.android.library")
}

android {
    namespace = "dev.tomasflopes.musicplayer.playback"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.taste)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)
}
