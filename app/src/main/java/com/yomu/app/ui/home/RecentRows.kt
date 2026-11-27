package com.yomu.app.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.ui.theme.LocalYomuColors
import com.yomu.app.ui.theme.PaperSheet
import com.yomu.app.ui.theme.PaperSurface
import java.text.DateFormat
import java.util.Date

/** The saved page whose full text is open, remembered by id so it survives the list changing under it. */
internal class OpenRecent(private val id: MutableState<Long?>) {
    fun open(translation: TranslationEntity) { id.value = translation.id }

    /** Shows the open page's sheet, if it is still among [translations]. */
    @Composable
    fun Sheet(translations: List<TranslationEntity>) {
        translations.firstOrNull { it.id == id.value }?.let { RecentTextSheet(it) { id.value = null } }
    }
}

@Composable
internal fun rememberOpenRecent(): OpenRecent {
    val id = rememberSaveable { mutableStateOf<Long?>(null) }
    return remember(id) { OpenRecent(id) }
}

/** Home's Recent rows in one card. */
@Composable
internal fun RecentCard(translations: List<TranslationEntity>, onOpen: (TranslationEntity) -> Unit) {
    PaperSurface(Modifier.fillMaxWidth()) {
        Column {
            translations.forEachIndexed { index, translation ->
                if (index > 0) Divider(Modifier.padding(horizontal = 16.dp))
                RecentRow(translation) { onOpen(translation) }
            }
        }
    }
}

/** A saved page: its thumbnail, the first two lines of its translation, and when, how many bubbles and which language. */
@Composable
internal fun RecentRow(translation: TranslationEntity, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PageThumbnail(translation.sourceImagePath)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    remember(translation.translatedText) { recentPreview(translation.translatedText) },
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium
                )
                Text(detail(translation), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = muted)
        }
    }
}

/** The page image, or a plain stand-in page while there is none. */
@Composable
private fun PageThumbnail(path: String) {
    val colors = LocalYomuColors.current
    val image = remember(path) { pageImage(path) }
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.size(44.dp, 60.dp).clip(shape).background(Color(colors.paper)).border(1.dp, Color(colors.edge), shape)) {
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().weight(3f).background(Color(colors.edge)))
                Box(Modifier.fillMaxWidth().weight(2f).background(Color(colors.edge)))
            }
        }
    }
}

@Composable
private fun RecentTextSheet(translation: TranslationEntity, onDismiss: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    PaperSheet("Translation", onDismiss) {
        SelectionContainer {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(detail(translation), style = MaterialTheme.typography.bodySmall, color = muted)
                remember(translation.translatedText) { recentBubbles(translation.translatedText) }.forEach { bubble ->
                    Column {
                        Text(bubble.translated, style = MaterialTheme.typography.bodyLarge)
                        if (bubble.original.isNotBlank()) Text(bubble.original, style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        }
    }
}

/** Today's pages show the time alone; older ones need their date too. */
private fun detail(translation: TranslationEntity): String {
    val format = if (DateUtils.isToday(translation.createdAt)) DateFormat.getTimeInstance(DateFormat.SHORT)
    else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return recentDetail(format.format(Date(translation.createdAt)), translation.bubbleCount, translation.targetLanguage)
}
