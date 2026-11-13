plugins {
    `kotlin-dsl`
}

group = "dev.tomasflopes.musicplayer.buildlogic"

kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "musicplayer.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("androidLibrary") {
            id = "musicplayer.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "musicplayer.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
