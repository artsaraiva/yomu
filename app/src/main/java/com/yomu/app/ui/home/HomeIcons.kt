package com.yomu.app.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// The icons Home and setup draw themselves; Material's core set has no globe, phone outline, stop square, sparkle or download arrow.

private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    path(
        stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block
    )
}.build()

internal val Globe: ImageVector = lineIcon("Globe") {
    moveTo(20.5f, 12f)
    arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f)
    arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f)
    moveTo(3.5f, 12f)
    horizontalLineToRelative(17f)
    moveTo(12f, 3.5f)
    curveToRelative(3f, 3f, 3f, 14f, 0f, 17f)
    moveTo(12f, 3.5f)
    curveToRelative(-3f, 3f, -3f, 14f, 0f, 17f)
}

internal val PhoneOutline: ImageVector = lineIcon("PhoneOutline") {
    moveTo(9f, 3f)
    horizontalLineTo(15f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
    verticalLineTo(19f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
    horizontalLineTo(9f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
    verticalLineTo(5f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
    close()
    moveTo(11f, 18f)
    horizontalLineToRelative(2f)
}

internal val StopSquare: ImageVector = ImageVector.Builder("StopSquare", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(8.5f, 7f)
        horizontalLineTo(15.5f)
        arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
        verticalLineTo(15.5f)
        arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
        horizontalLineTo(8.5f)
        arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
        verticalLineTo(8.5f)
        arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
        close()
    }
}.build()

/** A four-point sparkle with concave sides. */
internal val Sparkle: ImageVector = ImageVector.Builder("Sparkle", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        quadTo(12.6f, 11.4f, 22f, 12f)
        quadTo(12.6f, 12.6f, 12f, 22f)
        quadTo(11.4f, 12.6f, 2f, 12f)
        quadTo(11.4f, 11.4f, 12f, 2f)
        close()
    }
}.build()

internal val DownloadArrow: ImageVector = lineIcon("DownloadArrow") {
    moveTo(12f, 4f)
    verticalLineToRelative(11f)
    moveTo(7f, 11f)
    lineToRelative(5f, 5f)
    lineToRelative(5f, -5f)
    moveTo(5f, 20f)
    horizontalLineToRelative(14f)
}
