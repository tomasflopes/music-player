import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.configure
import org.gradle.process.CommandLineArgumentProvider

/** System property that tells a unit test where the merged manifest of the variant under test is. */
internal const val MERGED_MANIFEST_PROPERTY = "musicplayer.mergedManifest"

/** JVM unit tests (`src/test`) on Android modules; they run in `./gradlew check`, no emulator needed. */
internal fun Project.configureAndroidUnitTests() {
    configureUnitTests()
}

/**
 * Hands each variant's merged manifest (the one that ships, after every library manifest is merged in)
 * to that variant's unit tests. Wired through the AGP Variant API, so it follows task renames.
 */
internal fun Project.exposeMergedManifestToUnitTests() {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.configureTestTask { test ->
                test.jvmArgumentProviders.add(MergedManifestArgument(mergedManifest))
            }
        }
    }
}

/** Declares the manifest as a task input (so the test waits for it and reruns when it changes). */
private class MergedManifestArgument(
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val mergedManifest: Provider<RegularFile>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-D$MERGED_MANIFEST_PROPERTY=${mergedManifest.get().asFile.absolutePath}")
}
