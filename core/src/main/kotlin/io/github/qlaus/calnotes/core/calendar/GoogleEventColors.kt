package io.github.qlaus.calnotes.core.calendar

/** Google Calendar's fixed event color palette (`colorId` → hex), as returned by the colors endpoint. */
object GoogleEventColors {
    private val palette = mapOf(
        "1" to "#7986CB", "2" to "#33B679", "3" to "#8E24AA", "4" to "#E67C73",
        "5" to "#F6BF26", "6" to "#F4511E", "7" to "#039BE5", "8" to "#616161",
        "9" to "#3F51B5", "10" to "#0B8043", "11" to "#D50000",
    )

    fun hexFor(colorId: String?): String? = colorId?.let { palette[it] }
}
