package io.github.qlaus.calnotes.data.repo

import java.time.ZoneId

/** Current time zone; an interface so tests can pin it. */
fun interface ZoneProvider {
    fun zone(): ZoneId
}
