package dev.tomasflopes.musicplayer.core.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TextNormalizerTest {
    @ParameterizedTest
    @CsvSource(
        "JAY-Z,jay z",
        "A\$AP Rocky,a ap rocky",
        "m.A.A.d city,m a a d city",
        "Song (Remix),song remix",
        "DAMN.,damn",
        "'  Beyoncé ',beyonce",
        "Ed,ed",
    )
    fun spaced(
        text: String,
        expected: String,
    ) = assertEquals(expected, TextNormalizer.spaced(text))

    @ParameterizedTest
    @CsvSource(
        "JAY-Z,jayz",
        "A\$AP Rocky,aap rocky",
        "m.A.A.d city,maad city",
        "N.W.A,nwa",
        "Mr. Morale,mr morale",
        "Song (Remix),song remix",
    )
    fun compact(
        text: String,
        expected: String,
    ) = assertEquals(expected, TextNormalizer.compact(text))

    @Test
    fun `folding the same text twice gives the same variants`() {
        val first = TextNormalizer.variants("A\$AP  Rocky")

        assertEquals(setOf("a ap rocky", "aap rocky", "asap rocky"), first)
        assertEquals(first, TextNormalizer.variants("A\$AP  Rocky"))
    }

    @Test
    fun `runs of spaces fold to one`() = assertEquals("a b", TextNormalizer.spaced("a    b"))

    @Test
    fun `variants of a plain name is one string`() = assertEquals(setOf("ed"), TextNormalizer.variants("Ed"))

    @Test
    fun `variants of a hyphenated name`() = assertEquals(setOf("jay z", "jayz"), TextNormalizer.variants("JAY-Z"))

    @Test
    fun `dollar sign also reads as s`() =
        assertEquals(setOf("a ap rocky", "aap rocky", "asap rocky"), TextNormalizer.variants("A\$AP Rocky"))

    @Test
    fun `dollar before a number keeps the plain form too`() =
        assertEquals(setOf("100 bill", "s100 bill"), TextNormalizer.variants("\$100 Bill"))

    @Test
    fun `only symbols gives nothing`() = assertEquals(emptySet<String>(), TextNormalizer.variants("(-.)"))

    @Test
    fun `digits and non-latin letters are kept`() {
        assertEquals("21 привет", TextNormalizer.spaced("21 Привет!"))
        assertEquals("21 привет", TextNormalizer.compact("21 Привет!"))
    }

    @Test
    fun tokens() = assertEquals(listOf("kendrick", "lamar"), TextNormalizer.tokens("kendrick lamar"))
}
