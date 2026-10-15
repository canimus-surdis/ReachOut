package com.canimussurdis.reachout.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Works out when weekly batches start. A batch day begins at midnight, local time. */
object Schedule {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** The most recent batch-day midnight at or before [nowMs]. */
    fun lastBatchStart(nowMs: Long, day: DayOfWeek, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(day))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** The next batch-day midnight strictly after today. */
    fun nextBatchStart(nowMs: Long, day: DayOfWeek, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.next(day))
            .atStartOfDay(zone).toInstant().toEpochMilli()
}
