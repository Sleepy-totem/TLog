package com.tlog.util

import com.tlog.data.WeekStart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object WeekHelper {
    fun weekBounds(ref: LocalDate, weekStart: WeekStart, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = weekStartDate(ref, weekStart)
        val end = start.plusDays(7)
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to
                end.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun weekStartDate(ref: LocalDate, weekStart: WeekStart): LocalDate = when (weekStart) {
        WeekStart.MONDAY -> ref.with(DayOfWeek.MONDAY).let { if (it.isAfter(ref)) it.minusWeeks(1) else it }
        WeekStart.SUNDAY -> ref.with(DayOfWeek.SUNDAY).let { if (it.isAfter(ref)) it.minusWeeks(1) else it }
    }

    fun toLocalDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    fun toEpochMillis(dt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dt.atZone(zone).toInstant().toEpochMilli()

    fun atTimeOnDate(date: LocalDate, time: LocalTime): ZonedDateTime =
        ZonedDateTime.of(date, time, ZoneId.systemDefault())
}
