package com.kasir

import java.text.NumberFormat
import java.util.Locale

object FormatterUtil {
    fun formatRupiah(amount: Int): String {
        val localeID = Locale("in", "ID")
        val numberFormat = NumberFormat.getCurrencyInstance(localeID)
        // Remove decimal places
        numberFormat.minimumFractionDigits = 0
        numberFormat.maximumFractionDigits = 0
        return numberFormat.format(amount.toDouble())
            .replace("Rp", "Rp ") // Add space after Rp
    }
}