package com.enginesense.app.owner

import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** What a shared payment SMS was for. Fuel becomes a fill-up; the rest are expenses. */
enum class PaymentKind(val label: String) { FUEL("Fuel"), TOLL("Toll"), PARKING("Parking"), WASH("Wash"), SERVICE("Service"), PARTS("Parts"), OTHER("Other") }

data class ParsedPayment(val amountInr: Int, val kind: PaymentKind, val date: LocalDate?, val merchant: String?)

/**
 * Reads a bank, UPI or FASTag SMS the owner shared into the app (long-press → Share → GetAutoSure).
 * The app never reads the inbox: that needs a Play permission car apps don't get. Only debits are
 * expenses — a credit or refund SMS returns null.
 */
object PaymentSmsParser {

    private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val DATE = Regex("""\b(\d{1,2})[-/ ]([A-Za-z]{3}|\d{1,2})[-/ ](\d{2,4})\b""")
    private val MERCHANT = Regex("""\b(?:at|to|towards|for|info:?)\s+([A-Za-z0-9&.' ]{3,40}?)(?=\s+(?:on|via|ref|upi|avl|txn|-)\b|[.,;]|$)""", RegexOption.IGNORE_CASE)

    private val KINDS: List<Pair<PaymentKind, List<String>>> = listOf(
        PaymentKind.TOLL to listOf("fastag", "toll", "nhai", "netc", "plaza"),
        PaymentKind.PARKING to listOf("parking", "park+", "parkplus"),
        PaymentKind.FUEL to listOf("petrol", "diesel", "fuel", "hpcl", "iocl", "bpcl", "indian oil", "indianoil", "bharat petroleum", "hindustan petroleum", "nayara", "shell", "jio-bp", "jio bp", "filling station", "service station", "cng"),
        PaymentKind.WASH to listOf("car wash", "carwash", "wash", "detailing", "spa"),
        PaymentKind.PARTS to listOf("spares", "spare parts", "auto parts", "tyre", "tyres", "battery", "boodmo"),
        PaymentKind.SERVICE to listOf("service", "garage", "motors", "workshop", "automobiles", "gomechanic", "car care")
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedPayment? {
        val lower = text.lowercase(Locale.ROOT)
        val isDebit = listOf("debited", "spent", "paid", "deducted", "debit", "sent", "purchase", "txn of", "charged").any { it in lower }
        val isCredit = listOf("credited", "refund", "received", "cashback").any { it in lower }
        if (isCredit && !isDebit) return null
        // A FASTag or wallet top-up isn't spending yet; the tolls it pays for come in their own SMS.
        if ("recharge" in lower || "top-up" in lower || "topup" in lower) return null

        // The first amount is the transaction; skip any that's the balance ("Avl Bal INR 5,432").
        val amount = AMOUNT.findAll(text).firstOrNull { m ->
            val before = lower.substring((m.range.first - 14).coerceAtLeast(0), m.range.first)
            "bal" !in before && "limit" !in before
        }?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.let { Math.round(it).toInt() } ?: return null
        if (amount <= 0) return null

        // Toll SMS often mention the bank's "service"; check the narrower kinds first.
        val kind = KINDS.firstOrNull { (_, words) -> words.any { it in lower } }?.first ?: PaymentKind.OTHER
        val merchant = MERCHANT.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && !it.first().isDigit() }
        return ParsedPayment(amount, kind, parseDate(text, today), merchant)
    }

    private fun parseDate(text: String, today: LocalDate): LocalDate? {
        val m = DATE.find(text) ?: return null
        val (d, mon, y) = m.destructured
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        val month = mon.toIntOrNull() ?: runCatching {
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM").toFormatter(Locale.ENGLISH).parse(mon).get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
        }.getOrNull() ?: return null
        val date = runCatching { LocalDate.of(year, month, d.toInt()) }.getOrNull() ?: return null
        // A date in the future or years back is a misread (an account number, say): use today instead.
        return date.takeIf { !it.isAfter(today) && it.isAfter(today.minusYears(1)) }
    }
}
