package dev.tomasflopes.musicplayer.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * The onion rules from CLAUDE.md, written once and run by every module's own `ArchitectureTest` (ADR 0013).
 *
 * - `:core:*` is pure Kotlin: no Android types, nothing from the outer layers.
 * - `:core:model` is the centre: it depends on no other core module. Every other core module may use `:core:model`
 *   and nothing else from core.
 * - `:data` and `:playback` depend inward only: never on each other, on `:app` code, or on a sibling outer layer.
 */
object OnionRules {
    private const val ROOT = "dev.tomasflopes.musicplayer"
    private const val MODEL = "model"
    private val coreModules = listOf(MODEL, "tags", "search", "taste", "analysis")
    private val outerLayers = listOf("data", "playback", "ui", "di")

    /** Checks one `:core:<module>`; call it from that module's tests. */
    fun checkCore(module: String) {
        val classes = import("$ROOT.core.$module")
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("android..", "androidx..", *packages(outerLayers.map { "$ROOT.$it.." }))
            .because(":core:$module is pure Kotlin and must not know Android or the outer layers")
            .check(classes)

        val forbiddenCore = coreModules.filter { it != module && it != MODEL }
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*packages(forbiddenCore.map { "$ROOT.core.$it.." }))
            .because(":core:$module may depend on :core:$MODEL only, never on a sibling core module")
            .check(classes)

        if (module == MODEL) {
            noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(*packages(coreModules.filter { it != MODEL }.map { "$ROOT.core.$it.." }))
                .because(":core:$MODEL is the centre of the onion and depends on nothing outward")
                .check(classes)
        }
    }

    /** Checks an outer module (`data` or `playback`): it may use core, never a sibling outer layer. */
    fun checkOuter(layer: String) {
        require(layer in outerLayers) { "Unknown layer $layer" }
        val classes = import("$ROOT.$layer")
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*packages(outerLayers.filter { it != layer }.map { "$ROOT.$it.." }))
            .because(":$layer depends inward on :core:* only, never on a sibling or on :app")
            .check(classes)
    }

    private fun import(modulePackage: String): JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages(modulePackage)
            .also { check(it.isNotEmpty()) { "No classes found under $modulePackage; the rule would pass vacuously" } }

    private fun packages(names: List<String>): Array<String> = names.toTypedArray()
}
