plugins {
    id("musicplayer.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    // Ports in this module expose Flow (plan Task 8).
    api(libs.kotlinx.coroutines.core)

    // The onion rules live in testFixtures so every module's ArchitectureTest runs the same ones (ADR 0013).
    testFixturesApi(libs.archunit.junit5)
}
