package com.yomu.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PageReportShareTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a new report removes the earlier one and takes a name of its own`() {
        val reports = File(folder.root, "reports").apply { mkdirs() }
        val earlier = File(reports, "yomu-page-report-1000.zip").apply { writeText("earlier page") }

        val next = nextReportFile(reports, now = 2000)

        assertEquals(emptyList<String>(), reports.list()?.toList())
        assertNotEquals(earlier.name, next.name)
        assertEquals(reports, next.parentFile)
    }

    @Test
    fun `the first report creates the reports folder`() {
        val reports = File(folder.root, "reports")

        nextReportFile(reports, now = 1000)

        assertEquals(true, reports.isDirectory)
    }
}
