package com.nackie.ownexpenses.importing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvReaderTest {
    @Test
    fun readsQuotedFieldsWithDelimitersQuotesAndLineBreaks() {
        val records = readCsv("a,\"b, c\",\"say \"\"hi\"\"\"\r\n\"two\nlines\",,x\r\nlast")

        assertEquals(listOf("a", "b, c", "say \"hi\""), records[0].fields)
        assertEquals(listOf("two\nlines", "", "x"), records[1].fields)
        assertEquals(listOf("last"), records[2].fields)
        assertEquals(listOf(1, 2, 4), records.map { it.line })
    }

    @Test
    fun blankAndEmptyQuotedLinesAreBlank() {
        val records = readCsv("﻿h1,h2\n\n\"\"\nx,y\n")

        assertEquals(listOf("h1", "h2"), records[0].fields)
        assertTrue(records[1].isBlank)
        assertTrue(records[2].isBlank)
        assertEquals(listOf("x", "y"), records[3].fields)
        assertEquals(4, records.size)
    }

    @Test
    fun otherDelimiter() {
        assertEquals(listOf("1,5", "2"), readCsv("1,5;2", delimiter = ';').single().fields)
    }
}
