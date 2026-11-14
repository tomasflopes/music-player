plugins {
    id("musicplayer.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    testImplementation(testFixtures(projects.core.model))
}
