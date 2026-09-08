package com.yomu.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

enum class PaperElevation(val offset: Int) { Page(0), Card(3), Popup(5), OverlayControl(1) }

@Composable
fun PaperSurface(
    modifier: Modifier = Modifier,
    elevation: PaperElevation = PaperElevation.Card,
    content: @Composable () -> Unit
) {
    val colors = LocalYomuColors.current
    val page = elevation == PaperElevation.Page
    val shape = if (page) RectangleShape else MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val offset = elevation.offset.dp.toPx()
            translate(left = offset / 2, top = offset) {
                drawOutline(outline, Color(colors.ink).copy(alpha = 0.12f))
            }
        },
        color = Color(if (page) colors.paper else colors.paperRaised), contentColor = Color(colors.ink),
        shape = shape, border = if (page) null else BorderStroke(1.dp, Color(colors.edge)), content = content
    )
}

@Composable
fun ChromeContent(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    PaperSurface(modifier = modifier.fillMaxSize(), elevation = PaperElevation.Page) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp), content = content)
        }
    }
}
