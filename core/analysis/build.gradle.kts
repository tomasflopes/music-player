plugins {
    id("musicplayer.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(testFixtures(projects.core.model))
}
