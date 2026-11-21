package com.yomu.app.capture

import android.media.Image
import java.nio.ByteBuffer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowImageReader

/**
 * A captured image that can be turned into a bitmap. Robolectric's own shadow gives an image its reader's size but no
 * pixels, so this one adds a single blank RGBA plane of that size.
 */
@Implements(className = "android.media.ImageReader\$SurfaceImage")
class ShadowCapturedImage : ShadowImageReader.ShadowSurfaceImage() {
    @Implementation
    protected fun getPlanes(): Array<Image.Plane> {
        val plane = mock(Image.Plane::class.java)
        `when`(plane.buffer).thenReturn(ByteBuffer.allocate(getWidth() * getHeight() * BYTES_PER_PIXEL))
        `when`(plane.pixelStride).thenReturn(BYTES_PER_PIXEL)
        `when`(plane.rowStride).thenReturn(getWidth() * BYTES_PER_PIXEL)
        return arrayOf(plane)
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
