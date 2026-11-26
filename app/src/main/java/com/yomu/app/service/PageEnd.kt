package com.yomu.app.service

import com.yomu.app.translation.ReaderAction
import com.yomu.app.translation.Translator
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.translation.readerFailure
import com.yomu.pipeline.translation.untranslatedNotice

/**
 * How a finished page ends on screen: [shown] is the page to draw, or null when there is none worth drawing;
 * [failure] is the reader's message for its error, or null when there is none; [statusLine] is the text left
 * on the status line, or null when nothing needs saying; [action] is what tapping the status line does, or null when
 * the line is not for tapping.
 */
internal data class PageEnd(val shown: PipelineResult?, val failure: String?, val statusLine: String?, val action: ReaderAction? = null)

/**
 * How [result] ends on screen. Its failure is the message [translator], the translator that took the page, gives
 * for its error code, whatever the outcome, else the phone's own message (#509). A failed page is drawn only when
 * at least one bubble was answered, as a server that failed mid-reply keeps the bubbles it streamed. A message with an
 * action puts its label on the status line's last line (#534).
 */
internal fun pageEnd(result: PipelineResult?, translator: Translator?): PageEnd {
    val translation = result?.translationResult
    val message = translation?.errorCode?.let { translator?.readerMessage(it) }
    val failure = translation?.let { page -> message?.text ?: page.readerFailure() }
    val shown = result?.takeIf { failure == null || it.translationResult.translations.any { bubble -> bubble.answered } }
    val statusLine = when {
        shown != null -> failure ?: shown.translationResult.untranslatedNotice()
        // A null result has no failure here: the pipeline already put its own "Failed: …" on the status line.
        else -> failure?.let { "Failed: $it" }
    }
    val action = message?.action
    return PageEnd(shown, failure, action?.let { "$statusLine\n${it.label}" } ?: statusLine, action)
}
