package com.yomu.pipeline.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NpyFloatsTest {

    @Test
    fun `reads the shape and little-endian float32 values of a version 1 npy file`() {
        val matrix = NpyFloats.parse(npy("{'descr': '<f4', 'fortran_order': False, 'shape': (2, 3), }", floatArrayOf(1f, 2f, 3f, 4f, 5f, 6.5f)))

        assertEquals(listOf(2, 3), matrix.shape)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6.5f), matrix.values, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `refuses a file that is not little-endian float32`() {
        NpyFloats.parse(npy("{'descr': '<f8', 'fortran_order': False, 'shape': (1,), }", floatArrayOf(1f, 2f)))
    }

    private fun npy(header: String, values: FloatArray): ByteArray {
        val total = (10 + header.length + 1 + 63) / 64 * 64
        val padded = header.padEnd(total - 11) + "\n"
        val buffer = ByteBuffer.allocate(10 + padded.length + values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x93.toByte()).put("NUMPY".toByteArray()).put(1).put(0).putShort(padded.length.toShort())
        buffer.put(padded.toByteArray())
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}
