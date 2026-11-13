import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** The :app module: Compose + Hilt. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        configureQuality()
        configureAndroidUnitTests()
        configureNoNetworkGuard()
        exposeMergedManifestToUnitTests()

        extensions.configure<ApplicationExtension> {
            compileSdk = ANDROID_SDK
            defaultConfig {
                minSdk = ANDROID_SDK
                targetSdk = ANDROID_SDK
                versionCode = 1
                versionName = "0.1.0"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
            buildFeatures { compose = true }
        }

        dependencies {
            "implementation"(platform(libs.lib("androidx-compose-bom")))
            "implementation"(libs.lib("androidx-compose-ui"))
            "implementation"(libs.lib("androidx-compose-ui-tooling-preview"))
            "implementation"(libs.lib("androidx-compose-material3"))
            "implementation"(libs.lib("androidx-activity-compose"))
            "implementation"(libs.lib("hilt-android"))
            "ksp"(libs.lib("hilt-compiler"))
            "debugImplementation"(libs.lib("androidx-compose-ui-tooling"))
        }
    }
}
