package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.layout.EventColumns
import io.github.qlaus.calnotes.core.layout.TimedItem
import kotlin.test.Test
import kotlin.test.assertEquals

class EventColumnsTest {
    private fun place(vararg items: Triple<String, Long, Long>) =
        EventColumns.layout(items.map { TimedItem(it.first, it.second, it.third) }).associateBy { it.item }

    @Test
    fun nonOverlappingEventsUseFullWidth() {
        val r = place(Triple("a", 0, 10), Triple("b", 10, 20))
        assertEquals(1, r.getValue("a").columnCount)
        assertEquals(1, r.getValue("b").columnCount)
    }

    @Test
    fun overlappingEventsSitSideBySide() {
        val r = place(Triple("a", 0, 10), Triple("b", 5, 15))
        assertEquals(0, r.getValue("a").column)
        assertEquals(1, r.getValue("b").column)
        assertEquals(2, r.getValue("b").columnCount)
    }

    @Test
    fun freedColumnIsReusedInsideCluster() {
        // a overlaps b and c, b ends before c starts -> c reuses column 1.
        val r = place(Triple("a", 0, 30), Triple("b", 0, 10), Triple("c", 15, 20))
        assertEquals(2, r.getValue("c").columnCount)
        assertEquals(1, r.getValue("c").column)
    }

    @Test
    fun separateClustersHaveIndependentColumnCounts() {
        val r = place(Triple("a", 0, 10), Triple("b", 5, 10), Triple("c", 20, 30))
        assertEquals(2, r.getValue("a").columnCount)
        assertEquals(1, r.getValue("c").columnCount)
    }
}
