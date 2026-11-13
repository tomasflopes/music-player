// Plugin versions live in gradle/libs.versions.toml. They are put on the classpath here
// (apply false) so the convention plugins in build-logic/ can apply them by id.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Tests that need a running emulator or phone (Room, MediaStore, playback). Not part of `check` (plan C2, ADR 0009).
val deviceCheck =
    tasks.register("deviceCheck") {
        group = "verification"
        description = "Runs every module's instrumented tests on the connected emulator or phone."
    }
subprojects {
    val modulePath = path
    listOf("com.android.library", "com.android.application").forEach { plugin ->
        pluginManager.withPlugin(plugin) {
            deviceCheck.configure { dependsOn("$modulePath:connectedDebugAndroidTest") }
        }
    }
}
