plugins {
    id("musicplayer.android.application")
}

android {
    namespace = "dev.tomasflopes.musicplayer"
    defaultConfig {
        applicationId = "dev.tomasflopes.musicplayer"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.taste)
    implementation(projects.core.search)
    implementation(projects.core.analysis)
    implementation(projects.data)
    implementation(projects.playback)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(projects.core.tags)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.media3.session)
    androidTestImplementation(libs.hilt.android)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(testFixtures(projects.core.tags))
}
