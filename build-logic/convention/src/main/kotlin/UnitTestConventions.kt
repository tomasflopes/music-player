import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** JUnit Platform and the same test libraries in every module, so a test looks the same wherever it lives. */
internal fun Project.configureUnitTests() {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    dependencies {
        "testImplementation"(platform(libs.lib("junit-bom")))
        "testImplementation"(libs.lib("junit-jupiter"))
        "testImplementation"(libs.lib("kotlinx-coroutines-test"))
        "testImplementation"(libs.lib("turbine"))
        "testRuntimeOnly"(libs.lib("junit-platform-launcher"))
    }
}
