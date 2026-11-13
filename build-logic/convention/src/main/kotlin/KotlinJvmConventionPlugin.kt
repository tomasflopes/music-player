import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure-Kotlin modules (:core:*). No Android imports allowed; tests run on the JVM with JUnit 5. */
class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureQuality()

        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(JAVA_VERSION) }
        configureUnitTests()
    }
}
