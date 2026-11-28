package com.yomu.app.overlay

import android.graphics.Point

/** The progress capsule's window in screen pixels; [growsLeft] puts the floating button at its right end. */
internal data class CapsulePlacement(val x: Int, val y: Int, val width: Int, val height: Int, val growsLeft: Boolean)

/**
 * Places the capsule over the floating button at [button] ([buttonSize] square), growing to the side with more
 * room on a [screen] that keeps [margin] free at its edges. [sizeWithin] returns the size the capsule takes when it
 * may be at most that wide. A capsule taller than the button is centred on it, then pushed back on screen.
 */
internal fun capsulePlacement(
    button: Point,
    buttonSize: Int,
    screen: Point,
    margin: Int,
    sizeWithin: (maxWidth: Int) -> Point
): CapsulePlacement {
    val roomLeft = button.x + buttonSize - margin
    val roomRight = screen.x - margin - button.x
    val growsLeft = roomLeft > roomRight
    val size = sizeWithin(maxOf(roomLeft, roomRight))
    val width = size.x.coerceIn(buttonSize, maxOf(roomLeft, roomRight, buttonSize))
    val height = maxOf(size.y, buttonSize)
    return CapsulePlacement(
        x = if (growsLeft) button.x + buttonSize - width else button.x,
        y = (button.y - (height - buttonSize) / 2).coerceIn(margin, (screen.y - margin - height).coerceAtLeast(margin)),
        width = width,
        height = height,
        growsLeft = growsLeft
    )
}
