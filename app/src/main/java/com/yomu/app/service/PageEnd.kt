package com.yomu.app.service

import com.yomu.app.translation.Translator
import com.yomu.pipeline.PipelineResult
import com.yomu.pipeline.translation.readerFailure
import com.yomu.pipeline.translation.untranslatedNotice

/**
 * How a finished page ends on screen: [shown] is the page to draw, or null when there is none worth drawing;
 * [failure] is the reader's message for its error, or null when there is none; [statusLine] is the text left
 * on the status line, or null when nothing needs saying.
 */
internal data class PageEnd(val shown: PipelineResult?, val failure: String?, val statusLine: String?)

/**
 * Ends [result] as [translator], the translator that took it, explains it (#509): that translator's message for
 * the page's error code, whatever its outcome, else the phone's own messages. A page with a failure is drawn only
 * when at least one bubble was answered, as a server that failed mid-reply keeps the bubbles it streamed.
 */
internal fun pageEnd(result: PipelineResult?, translator: Translator?): PageEnd {
    val translation = result?.translationResult
    val failure = translation?.let { page -> page.errorCode?.let { translator?.readerMessage(it) } ?: page.readerFailure() }
    val shown = result?.takeIf { failure == null || it.translationResult.translations.any { bubble -> bubble.answered } }
    val statusLine = when {
        shown != null -> failure ?: shown.translationResult.untranslatedNotice()
        // A null result already put the pipeline's own "Failed: …" on the status line.
        else -> failure?.let { "Failed: $it" }
    }
    return PageEnd(shown, failure, statusLine)
}
