package com.futsch1.medtimer.feature.ui.overview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailPillSpansTest {

    @Test
    fun `should draw no pill across the break between the open and the resolved section`() {
        val anchors = listOf(
            RailAnchor(100f, inResolvedSection = false),
            RailAnchor(200f, inResolvedSection = false),
            RailAnchor(400f, inResolvedSection = true),
            RailAnchor(500f, inResolvedSection = true),
        )

        val spans = spans(anchors)

        assertEquals(listOf(110f..190f, 410f..490f), spans.sortedBy { it.start })
        assertTrue("no pill spans the section break", spans.none { it.start < 300f && it.endInclusive > 300f })
    }

    @Test
    fun `should draw one continuous rail when all rows are in one section`() {
        val anchors = listOf(
            RailAnchor(400f, inResolvedSection = true),
            RailAnchor(100f, inResolvedSection = true),
            RailAnchor(200f, inResolvedSection = true),
        )

        val spans = spans(anchors)

        assertEquals(listOf(110f..190f, 210f..390f), spans.sortedBy { it.start })
    }

    @Test
    fun `should shrink the inset so a short corridor still gets a pill`() {
        val anchors = listOf(RailAnchor(0f, inResolvedSection = false), RailAnchor(30f, inResolvedSection = false))

        val spans = spans(anchors)

        assertEquals(listOf(10f..20f), spans)
    }

    @Test
    fun `should draw no pill when the buttons touch`() {
        val anchors = listOf(RailAnchor(0f, inResolvedSection = false), RailAnchor(10f, inResolvedSection = false))

        assertEquals(emptyList<ClosedFloatingPointRange<Float>>(), spans(anchors))
    }

    private fun spans(anchors: List<RailAnchor>) =
        railPillSpans(anchors, buttonRadius = BUTTON_RADIUS, pillWidth = PILL_WIDTH, maxInset = MAX_INSET)

    private companion object {
        const val BUTTON_RADIUS = 5f
        const val PILL_WIDTH = 2f
        const val MAX_INSET = 5f
    }
}
