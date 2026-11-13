package dev.tomasflopes.musicplayer.core.taste.shuffle

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlainShuffleTest : ShuffleStrategyContract() {
    override fun strategy(): ShuffleStrategy = PlainShuffle()

    @Test
    fun `seed 37 does not return the input order for 100 items`() {
        val items = refs(100)

        assertNotEquals(items, strategy().order(items, Random(37)))
    }

    @Test
    fun `different seeds give different orders`() {
        val items = refs(100)

        assertNotEquals(strategy().order(items, Random(1)), strategy().order(items, Random(2)))
    }
}
