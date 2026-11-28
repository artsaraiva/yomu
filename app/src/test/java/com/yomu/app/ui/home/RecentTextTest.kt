package com.yomu.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTextTest {
    @Test
    fun `a saved page reads back as its bubbles, Japanese beside translation`() {
        val saved = "[0] 開けるなと言っただろ → I told you not to open that door.\n[1] もう遅い → It's too late."

        assertEquals(
            listOf(RecentBubble("開けるなと言っただろ", "I told you not to open that door."), RecentBubble("もう遅い", "It's too late.")),
            recentBubbles(saved)
        )
    }

    @Test
    fun `a translation that runs over several lines stays in its bubble`() {
        assertEquals(
            listOf(RecentBubble("あ", "Ah.\nWait."), RecentBubble("い", "Yes.")),
            recentBubbles("[0] あ → Ah.\nWait.\n[1] い → Yes.")
        )
    }

    @Test
    fun `Japanese read over several lines stays in its bubble too`() {
        assertEquals(
            listOf(RecentBubble("開ける\nな", "Don't open it."), RecentBubble("い", "Yes.")),
            recentBubbles("[0] 開ける\nな → Don't open it.\n[1] い → Yes.")
        )
    }

    @Test
    fun `text that isn't in the saved form is shown as it is`() {
        assertEquals(listOf(RecentBubble("", "Just some text")), recentBubbles("Just some text"))
    }

    @Test
    fun `the row previews the translations alone, in reading order`() {
        assertEquals(
            "I told you not to open that door. It's too late.",
            recentPreview("[0] 開けるなと言っただろ → I told you not to open that door.\n[1] もう遅い → It's too late.")
        )
    }

    @Test
    fun `the row's detail line gives the time, the bubble count and the language`() {
        assertEquals("14:32 · 9 bubbles · English", recentDetail("14:32", 9, "en"))
        assertEquals("14:30 · 1 bubble · Spanish", recentDetail("14:30", 1, "es"))
    }

    @Test
    fun `a language Yomu no longer knows is left off the detail line`() {
        assertEquals("14:32 · 9 bubbles", recentDetail("14:32", 9, "xx"))
    }

    @Test
    fun `today's count reads as pages`() {
        assertEquals("12 pages today", pagesTodayLabel(12))
        assertEquals("1 page today", pagesTodayLabel(1))
        assertEquals("0 pages today", pagesTodayLabel(0))
    }
}
