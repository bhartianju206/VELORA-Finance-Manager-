package com.example.velora.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyHelper {

    /**
     * Formats integer paise into Indian currency string: e.g. ₹1,25,000.00 or ₹450
     * 100 paise = ₹1.00
     */
    fun formatPaise(paise: Long, showDecimalsIfZero: Boolean = true): String {
        val isNegative = paise < 0
        val absPaise = Math.abs(paise)
        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100

        val formattedRupees = formatIndianNumber(rupees)
        val prefix = if (isNegative) "-₹" else "₹"

        return if (!showDecimalsIfZero && remainingPaise == 0L) {
            "$prefix$formattedRupees"
        } else {
            val paiseStr = String.format(Locale.US, "%02d", remainingPaise)
            "$prefix$formattedRupees.$paiseStr"
        }
    }

    /**
     * Formats a positive number using the Indian numbering system:
     * e.g. 100000 -> "1,00,000"
     */
    private fun formatIndianNumber(number: Long): String {
        val s = number.toString()
        if (s.length <= 3) return s
        val lastThree = s.substring(s.length - 3)
        val remaining = s.substring(0, s.length - 3)
        val sb = StringBuilder()
        var count = 0
        for (i in remaining.length - 1 downTo 0) {
            sb.append(remaining[i])
            count++
            if (count % 2 == 0 && i > 0) {
                sb.append(',')
            }
        }
        return sb.reverse().toString() + "," + lastThree
    }

    /**
     * Parses a string input (e.g. "1250" or "1250.50") into integer paise (125050L).
     * Returns null if invalid or negative.
     */
    fun parseToPaise(input: String): Long? {
        val clean = input.trim().replace("₹", "").replace(",", "").trim()
        if (clean.isEmpty()) return null
        return try {
            val parts = clean.split(".")
            val rupees = parts[0].toLongOrNull() ?: return null
            if (rupees < 0) return null
            val paise = if (parts.size > 1) {
                val pStr = parts[1].padEnd(2, '0').take(2)
                pStr.toLongOrNull() ?: 0L
            } else {
                0L
            }
            (rupees * 100) + paise
        } catch (e: Exception) {
            null
        }
    }
}

object DateUtils {
    private val fullDateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun formatDateTime(timestamp: Long): String = fullDateFormat.format(Date(timestamp))
    fun formatDate(timestamp: Long): String = dateOnlyFormat.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = timeOnlyFormat.format(Date(timestamp))
    fun formatMonthYear(timestamp: Long): String = monthYearFormat.format(Date(timestamp))
    fun formatIso(timestamp: Long): String = isoFormat.format(Date(timestamp))

    fun isToday(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        return formatIso(now) == formatIso(timestamp)
    }

    fun isSameMonth(timestamp: Long, monthTimestamp: Long): Boolean {
        val f = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return f.format(Date(timestamp)) == f.format(Date(monthTimestamp))
    }
}
