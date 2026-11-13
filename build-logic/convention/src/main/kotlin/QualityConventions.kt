import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** ktlint (format) + detekt (smells) on every module; both fail the build (guidelines 3.1). */
internal fun Project.configureQuality() {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    pluginManager.apply("dev.detekt")

    extensions.configure<KtlintExtension> {
        ignoreFailures.set(false)
    }
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }
}
