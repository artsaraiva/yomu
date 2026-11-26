package com.yomu.app.ui.navigation

import androidx.compose.runtime.Composable
import com.yomu.app.translation.AppDestination

/**
 * A screen a translator brings for one of its [AppDestination]s, registered through Hilt's `@IntoSet` beside the
 * translator itself (#534). The app adds each to its settings graph, so the screen opens wherever its destination is
 * named, and [content] runs in its own back-stack entry, where it can call `hiltViewModel()`.
 */
class TranslatorScreen(val destination: AppDestination, val content: @Composable (onBack: () -> Unit) -> Unit)
