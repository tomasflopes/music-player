plugins {
    id("musicplayer.kotlin.jvm")
}

dependencies {
    testImplementation(libs.archunit.junit5)
}

dependencies {
    // Ports in this module expose Flow (plan Task 8).
    api(libs.kotlinx.coroutines.core)
}
