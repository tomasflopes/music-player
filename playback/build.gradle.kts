plugins {
    id("musicplayer.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.tomasflopes.musicplayer.playback"
    // Generated 90 and 92 BPM click tracks for the transition deviceCheck (scripts/gen-transition-mp3s.sh).
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("testdata/transitions"))
}

dependencies {
    testImplementation(testFixtures(projects.core.model))
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(projects.core.model)
    implementation(projects.core.taste)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
