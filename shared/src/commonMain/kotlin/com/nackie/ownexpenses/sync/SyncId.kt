package com.nackie.ownexpenses.sync

import kotlin.random.Random

private const val UUID_BYTES = 16
private const val VERSION_BYTE = 6
private const val VARIANT_BYTE = 8
private val DASH_POSITIONS = setOf(4, 6, 8, 10)

/**
 * Random UUID v4 as text, e.g. `0b8f2c1e-6d0a-4c43-9f0e-3a5b7c9d1e2f`. Every category and record gets
 * one at creation and keeps it for life (also through backups): it is how the sync server recognises
 * a record it has already seen, so a resent record never becomes a duplicate.
 */
internal fun newSyncId(random: Random = Random.Default): String {
    val bytes = random.nextBytes(UUID_BYTES)
    bytes[VERSION_BYTE] = (bytes[VERSION_BYTE].toInt() and 0x0F or 0x40).toByte()
    bytes[VARIANT_BYTE] = (bytes[VARIANT_BYTE].toInt() and 0x3F or 0x80).toByte()
    return buildString(UUID_BYTES * 2 + DASH_POSITIONS.size) {
        bytes.forEachIndexed { index, byte ->
            if (index in DASH_POSITIONS) append('-')
            append(HEX[byte.toInt() shr 4 and 0x0F])
            append(HEX[byte.toInt() and 0x0F])
        }
    }
}

private const val HEX = "0123456789abcdef"
