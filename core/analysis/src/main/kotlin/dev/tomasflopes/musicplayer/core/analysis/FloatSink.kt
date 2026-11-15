package dev.tomasflopes.musicplayer.core.analysis

private const val INITIAL_CAPACITY = 1_024

/** A growable float array: the per-frame scalars of a song are collected here, never the PCM. */
internal class FloatSink(
    initialCapacity: Int = INITIAL_CAPACITY,
) {
    private var values = FloatArray(initialCapacity.coerceAtLeast(1))

    var size = 0
        private set

    fun add(value: Float) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }

    fun addAll(source: FloatArray) {
        for (value in source) add(value)
    }

    fun toArray(): FloatArray = values.copyOf(size)
}
