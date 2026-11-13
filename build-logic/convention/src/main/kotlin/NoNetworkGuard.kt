import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.work.DisableCachingByDefault

/** `group:module` prefixes that can never be on a runtime classpath: the app is 100% offline. */
internal val FORBIDDEN_NETWORK_COORDINATES =
    listOf(
        "com.squareup.okhttp3",
        "com.squareup.retrofit2",
        "io.ktor",
        "io.coil-kt.coil3:coil-network",
        "com.android.volley",
        "org.chromium.net",
        "com.google.android.gms",
        "com.google.firebase",
    )

/** Adds `verifyNoNetworkLibraries<Variant>` for every variant of an Android module and runs it in `check`. */
internal fun Project.configureNoNetworkGuard() {
    extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
        val variantName = variant.name.replaceFirstChar(Char::uppercase)
        val guard =
            tasks.register<VerifyNoNetworkLibrariesTask>("verifyNoNetworkLibraries$variantName") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = "Fails if a network library is on the ${variant.name} runtime classpath."
                modulePath.set(path)
                forbiddenPrefixes.set(FORBIDDEN_NETWORK_COORDINATES)
                runtimeGraph.set(variant.runtimeConfiguration.incoming.resolutionResult.rootComponent)
            }
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(guard) }
    }
}

/** Walks the resolved runtime dependency graph, direct and transitive, and fails on a forbidden coordinate. */
@DisableCachingByDefault(because = "Only inspects an already resolved dependency graph")
abstract class VerifyNoNetworkLibrariesTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val forbiddenPrefixes: ListProperty<String>

    @get:Input
    abstract val runtimeGraph: Property<ResolvedComponentResult>

    @TaskAction
    fun verify() {
        val forbidden = forbiddenPrefixes.get()
        val offenders = runtimeCoordinates().filter { coordinate -> forbidden.any(coordinate::startsWith) }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "${modulePath.get()}: network libraries are forbidden, the app is 100% offline. " +
                    "Found: ${offenders.joinToString()}",
            )
        }
    }

    private fun runtimeCoordinates(): Set<String> {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val pending = ArrayDeque(listOf(runtimeGraph.get()))
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (seen.add(component)) {
                component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .forEach { pending.add(it.selected) }
            }
        }
        return seen
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .map { "${it.group}:${it.module}" }
            .toSortedSet()
    }
}
