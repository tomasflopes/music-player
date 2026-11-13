package dev.tomasflopes.musicplayer.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NumberPairTest {
    @ParameterizedTest
    @CsvSource(
        "8,8,",
        "08,8,",
        "8/14,8,14",
        "' 8 ',8,",
        "8/,8,",
        "0,,",
        "'',,",
        "A1,,",
        "/14,,14",
        "-3,,",
        "8/0,8,",
        "99999999999,,",
    )
    fun `number pair`(
        text: String,
        number: Int?,
        total: Int?,
    ) {
        assertEquals(number to total, NumberPair.parse(text))
    }

    @Test
    fun `missing text is no number`() {
        assertEquals(null to null, NumberPair.parse(null))
    }
}
