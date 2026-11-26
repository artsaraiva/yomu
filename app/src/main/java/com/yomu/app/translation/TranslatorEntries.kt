package com.yomu.app.translation

/**
 * A screen a translator's entry opens, named by its navigation route (#534). The translator's module brings the screen
 * itself as a `TranslatorScreen`, and the app opens it wherever the destination is named.
 */
@JvmInline
value class AppDestination(val route: String)

/** What a translator tells the reader about a failed page, and optionally where the reader can go about it. */
data class ReaderMessage(val text: String, val action: ReaderAction? = null)

/** A reader message's way out: tapping the status line, which shows [label], opens [destination]. */
data class ReaderAction(val label: String, val destination: AppDestination)

/** An action a translator puts on Home while it needs the reader, such as a server to set up; tapping [label] opens [destination]. */
data class TranslatorHomeAction(val label: String, val destination: AppDestination)

/** A row a translator adds to Settings; [summary] is read each time Settings opens, so it can say how things stand. */
class TranslatorSettingsRow(val title: String, val summary: () -> String, val destination: AppDestination)

/** A row a translator adds to the model picker, under its name. [checked] marks the row that takes the next page. */
class TranslatorPickerEntry(val name: String, val subtitle: String?, val checked: Boolean, val pick: () -> TranslatorPick)

/** What picking a [TranslatorPickerEntry] does. */
sealed interface TranslatorPick {
    /** The entry's translator takes the next page. */
    data object Select : TranslatorPick

    /** Nothing changed. */
    data object Refused : TranslatorPick

    /** Nothing changed; the app opens [destination], such as a setup screen while the translator isn't ready. */
    data class Open(val destination: AppDestination) : TranslatorPick

    /** Nothing changed until the reader agrees to [message]; then [accept] picks, as the entry would have. */
    class Confirm(val title: String, val message: String, val confirmLabel: String, val accept: () -> TranslatorPick) : TranslatorPick
}
