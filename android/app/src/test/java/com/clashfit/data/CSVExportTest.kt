package com.clashfit.data

import com.clashfit.ui.screens.session.SummaryViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests CSV export escaping per RFC 4180.
 * Fields with comma, newline, or quote must be wrapped in quotes,
 * and internal quotes must be escaped as double-double.
 */
class CSVExportTest {

    @Test
    fun `escapeCsv wraps field with comma in quotes`() {
        val escaped = SummaryViewModel.escapeCsv("foo,bar")
        assertEquals("\"foo,bar\"", escaped)
    }

    @Test
    fun `escapeCsv wraps field with newline in quotes`() {
        val escaped = SummaryViewModel.escapeCsv("foo\nbar")
        assertEquals("\"foo\nbar\"", escaped)
    }

    @Test
    fun `escapeCsv escapes quotes as double-double`() {
        val escaped = SummaryViewModel.escapeCsv("foo\"bar")
        assertEquals("\"foo\"\"bar\"", escaped)
    }

    @Test
    fun `escapeCsv handles combined special characters`() {
        val escaped = SummaryViewModel.escapeCsv("foo,\"bar\"\nbaz")
        assertEquals("\"foo,\"\"bar\"\"\nbaz\"", escaped)
    }

    @Test
    fun `escapeCsv leaves simple field unchanged`() {
        val escaped = SummaryViewModel.escapeCsv("foobar")
        assertEquals("foobar", escaped)
    }

    @Test
    fun `escapeCsv handles empty string`() {
        val escaped = SummaryViewModel.escapeCsv("")
        assertEquals("", escaped)
    }
}
