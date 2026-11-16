package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.model.EventLinkMatcher
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.core.model.EventRef
import io.github.qlaus.calnotes.core.model.LinkKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventLinkMatcherTest {
    private val monday = EventRef("cal", "lecture_20261116T090000Z", "lecture", 1L)
    private val tuesday = EventRef("cal", "lecture_20261117T090000Z", "lecture", 2L)

    @Test
    fun instanceLinkMatchesOnlyThatOccurrence() {
        val link = LinkKey("cal", monday.eventId, "lecture", EventLinkScope.INSTANCE)
        assertTrue(EventLinkMatcher.matches(link, monday))
        assertFalse(EventLinkMatcher.matches(link, tuesday))
    }

    @Test
    fun seriesLinkMatchesEveryOccurrence() {
        val link = LinkKey("cal", "lecture", "lecture", EventLinkScope.SERIES)
        assertTrue(EventLinkMatcher.matches(link, monday))
        assertTrue(EventLinkMatcher.matches(link, tuesday))
    }

    @Test
    fun differentCalendarNeverMatches() {
        val link = LinkKey("other", "lecture", "lecture", EventLinkScope.SERIES)
        assertFalse(EventLinkMatcher.matches(link, monday))
    }

    @Test
    fun singleEventLink() {
        val single = EventRef("cal", "meeting")
        assertTrue(EventLinkMatcher.matches(LinkKey("cal", "meeting", null, EventLinkScope.INSTANCE), single))
    }
}
