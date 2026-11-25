package com.yomu.pipeline.ocr

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A float32 array saved by numpy, row-major, with its [shape]. Hayai ships its position table this way. */
internal class NpyFloats(val shape: List<Int>, val values: FloatArray) {

    companion object {
        private val SHAPE = Regex("""'shape':\s*\(([^)]*)\)""")

        fun read(file: File): NpyFloats = parse(file.readBytes())

        /** Parses a version 1 or 2 `.npy` file; refuses anything but C-order little-endian float32. */
        fun parse(bytes: ByteArray): NpyFloats {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bytes.size > 10 && bytes[0] == 0x93.toByte() && String(bytes, 1, 5) == "NUMPY") { "Not an npy file" }
            val major = bytes[6].toInt()
            val headerLength = if (major == 1) buffer.getShort(8).toInt() and 0xFFFF else buffer.getInt(8)
            val headerStart = if (major == 1) 10 else 12
            val header = String(bytes, headerStart, headerLength, Charsets.ISO_8859_1)
            require("'<f4'" in header && "'fortran_order': False" in header) { "Expected C-order little-endian float32: $header" }
            val shape = SHAPE.find(header)?.groupValues?.get(1)
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { it.toInt() }
                ?: throw IllegalArgumentException("No shape in npy header: $header")
            val values = FloatArray(shape.fold(1) { a, b -> a * b })
            buffer.position(headerStart + headerLength)
            buffer.asFloatBuffer().get(values)
            return NpyFloats(shape, values)
        }
    }
}
