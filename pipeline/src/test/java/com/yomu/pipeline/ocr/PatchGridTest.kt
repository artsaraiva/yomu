package com.yomu.pipeline.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected grids come from the #470 desktop replica's naflex_size and best_max_patches, capped at 192 (#510). */
class PatchGridTest {

    @Test
    fun `a speech bubble keeps its shape at the 192 patch tier`() {
        assertEquals(PatchGrid(rows = 17, columns = 11), PatchGrid.of(height = 166, width = 111))
        assertEquals(PatchGrid(rows = 20, columns = 9), PatchGrid.of(height = 192, width = 87))
    }

    @Test
    fun `a crop that would take 256 or 384 patches is capped at 192`() {
        assertEquals(PatchGrid(rows = 5, columns = 38), PatchGrid.of(height = 40, width = 300))
        assertEquals(PatchGrid(rows = 13, columns = 13), PatchGrid.of(height = 500, width = 500))
        assertEquals(PatchGrid(rows = 38, columns = 5), PatchGrid.of(height = 900, width = 120))
        assertEquals(PatchGrid(rows = 17, columns = 11), PatchGrid.of(height = 1200, width = 800))
        assertEquals(PatchGrid(rows = 4, columns = 47), PatchGrid.of(height = 60, width = 700))
    }

    @Test
    fun `a tiny crop is scaled up to the 64 patch floor of the 128 tier`() {
        assertEquals(PatchGrid(rows = 8, columns = 8), PatchGrid.of(height = 30, width = 30))
    }

    @Test
    fun `the grid's pixel size is sixteen pixels a patch`() {
        val grid = PatchGrid(rows = 17, columns = 11)

        assertEquals(272, grid.height)
        assertEquals(176, grid.width)
        assertEquals(187, grid.patches)
    }
}
