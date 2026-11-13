plugins {
    id("musicplayer.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.tomasflopes.musicplayer.data"
    // Room's exported schemas, read by MigrationTestHelper in the instrumented tests.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation(testFixtures(projects.core.model))
    implementation(projects.core.model)
    implementation(projects.core.tags)
    implementation(projects.core.search)
    implementation(projects.core.taste)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(testFixtures(projects.core.tags))
}
