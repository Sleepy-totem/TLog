package com.tlog.pay

import com.tlog.data.AppSettings

data class PaySplit(val regularHours: Double, val overtimeHours: Double, val regularPay: Double, val overtimePay: Double) {
    val totalHours get() = regularHours + overtimeHours
    val totalPay get() = regularPay + overtimePay
}

object PayCalc {
    fun split(totalHours: Double, s: AppSettings): PaySplit {
        val reg = totalHours.coerceAtMost(s.overtimeThreshold)
        val ot = (totalHours - s.overtimeThreshold).coerceAtLeast(0.0)
        return PaySplit(
            regularHours = reg,
            overtimeHours = ot,
            regularPay = reg * s.hourlyRate,
            overtimePay = ot * s.hourlyRate * s.overtimeMultiplier
        )
    }
}
