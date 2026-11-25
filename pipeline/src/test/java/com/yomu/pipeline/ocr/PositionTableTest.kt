package com.yomu.pipeline.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PositionTableTest {

    private val table = PositionTable(NpyFloats(listOf(16, 2), FloatArray(16 * 2) { it.toFloat() }))

    @Test
    fun `a grid gets the table resampled to its shape`() {
        assertArrayEquals(
            resamplePositions(FloatArray(32) { it.toFloat() }, side = 4, dimension = 2, rows = 2, columns = 3),
            table.at(PatchGrid(rows = 2, columns = 3)),
            0f
        )
    }

    @Test
    fun `a grid shape seen before is not resampled again`() {
        assertSame(table.at(PatchGrid(rows = 2, columns = 3)), table.at(PatchGrid(rows = 2, columns = 3)))
    }
}
