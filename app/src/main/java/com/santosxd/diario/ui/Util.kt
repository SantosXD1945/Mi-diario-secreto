package com.santosxd.diario.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar

/** Número único por día, por ejemplo 20261025. */
fun dayKey(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

fun firstOfMonth(ms: Long): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

fun shiftMonth(ms: Long, months: Int): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = ms
        add(Calendar.MONTH, months)
    }
    return c.timeInMillis
}

/** Días seguidos con al menos una entrada, contando hasta hoy (o hasta ayer si hoy aún no hay). */
fun streak(keys: Set<Int>): Int {
    val c = Calendar.getInstance()
    if (dayKey(c.timeInMillis) !in keys) c.add(Calendar.DAY_OF_YEAR, -1)
    var n = 0
    while (dayKey(c.timeInMillis) in keys) {
        n++
        c.add(Calendar.DAY_OF_YEAR, -1)
    }
    return n
}

fun pickDate(ctx: Context, current: Long, onPick: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = current }
    DatePickerDialog(
        ctx,
        { _, y, m, d ->
            val n = Calendar.getInstance().apply {
                timeInMillis = current
                set(y, m, d)
            }
            onPick(n.timeInMillis)
        },
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun pickTime(ctx: Context, current: Long, onPick: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = current }
    TimePickerDialog(
        ctx,
        { _, h, min ->
            val n = Calendar.getInstance().apply {
                timeInMillis = current
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, min)
            }
            onPick(n.timeInMillis)
        },
        c.get(Calendar.HOUR_OF_DAY),
        c.get(Calendar.MINUTE),
        true
    ).show()
}
