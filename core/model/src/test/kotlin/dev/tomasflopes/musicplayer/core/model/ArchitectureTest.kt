package dev.tomasflopes.musicplayer.core.model

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/** Keeps the innermost module platform-neutral, as required by the onion architecture. */
class ArchitectureTest {
    private val classes =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("dev.tomasflopes.musicplayer.core.model")

    @Test
    fun `core model does not depend on Android or outer modules`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "android..",
                "androidx..",
                "dev.tomasflopes.musicplayer.data..",
                "dev.tomasflopes.musicplayer.playback..",
                "dev.tomasflopes.musicplayer.ui..",
            ).check(classes)
    }
}
