package dev.tomasflopes.musicplayer.core.tags

/** `bytes("49 44 33")` → the three bytes `I`, `D`, `3`. Keeps byte-level tests readable. */
internal fun bytes(hex: String): ByteArray =
    hex
        .split(' ')
        .filter { it.isNotEmpty() }
        .map { it.toInt(radix = 16).toByte() }
        .toByteArray()
