package io.github.qlaus.calnotes.core.layout

/** An item occupying the half-open time interval [start, end). */
data class TimedItem<T>(val item: T, val start: Long, val end: Long)

/** Result of [EventColumns.layout]: the item is drawn in [column] of [columnCount] equal columns. */
data class PlacedItem<T>(val item: T, val column: Int, val columnCount: Int)

/**
 * Side-by-side layout for overlapping events in a day/week time grid.
 *
 * Items that transitively overlap form a cluster; inside a cluster each item takes the first
 * column that is free at its start time. All items in a cluster share the cluster's column count.
 */
object EventColumns {
    fun <T> layout(items: List<TimedItem<T>>): List<PlacedItem<T>> {
        val sorted = items.sortedWith(compareBy<TimedItem<T>> { it.start }.thenByDescending { it.end })
        val result = ArrayList<PlacedItem<T>>(sorted.size)
        var cluster = ArrayList<Pair<TimedItem<T>, Int>>()
        var columnEnds = ArrayList<Long>()
        var clusterEnd = Long.MIN_VALUE

        fun flush() {
            val count = columnEnds.size
            cluster.forEach { (item, column) -> result += PlacedItem(item.item, column, count) }
            cluster = ArrayList()
            columnEnds = ArrayList()
        }

        for (item in sorted) {
            if (cluster.isNotEmpty() && item.start >= clusterEnd) flush()
            val free = columnEnds.indexOfFirst { it <= item.start }
            val column = if (free >= 0) free else columnEnds.size
            if (column == columnEnds.size) columnEnds += item.end else columnEnds[column] = item.end
            cluster += item to column
            clusterEnd = if (cluster.size == 1) item.end else maxOf(clusterEnd, item.end)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }
}
