plugins {
    id("musicplayer.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.tomasflopes.musicplayer.data"
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
}
