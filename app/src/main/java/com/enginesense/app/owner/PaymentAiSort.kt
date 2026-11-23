package com.enginesense.app.owner

import java.util.Locale

/**
 * A payment alert the rules couldn't sort, waiting for AI (and, when AI isn't sure, the owner).
 * [text] is already masked by [PaymentAiSort.mask]; the raw alert is never kept.
 */
data class PendingPayment(
    val id: String,
    val date: String,
    val amountInr: Int,
    val merchant: String,
    val text: String,
    /** AI's guess (an [ExpenseCategory] name, "FUEL" or "NOT_CAR"); null until sorted. */
    val suggestion: String? = null,
    val confidence: String? = null
) {
    /** Sorted, but AI wasn't sure — the owner is asked. */
    val needsAnswer: Boolean get() = suggestion != null && suggestion != "NOT_CAR" && confidence != "high"
}

/** Privacy and merchant memory for AI sorting of payment alerts. Pure, so it's tested without Android. */
object PaymentAiSort {

    const val NOT_CAR = "NOT_CAR"
    const val MAX_PENDING = 30

    /**
     * The alert with everything private removed before it leaves the phone: account and card
     * numbers, UPI refs and other long digit runs, balances, phone numbers and email/UPI ids.
     * The amount stays — it's needed to sort — and so does the merchant name.
     */
    fun mask(text: String): String =
        text
            .replace(Regex("""(?i)(avl|avail|available|a/c|acct)?\s*bal(ance)?\.?\s*(is|:)?\s*(rs\.?|inr|₹)?\s*[0-9][0-9,]*(\.[0-9]{1,2})?"""), " ")
            .replace(Regex("""[\w.+-]+@[\w.-]+"""), "[id]")
            .replace(Regex("""(?i)(a/c|acct|account|card)\s*(no\.?|number)?\s*[xX*]*\d+"""), "$1 [hidden]")
            .replace(Regex("""[xX*]{2,}\d{2,}"""), "[hidden]")
            // Long digit runs (refs, phone numbers) even at a sentence end; not parts of an amount like 1,00,000.
            .replace(Regex("""(?<![\d,.])\d{6,}(?!\d|[,.]\d)"""), "[hidden]")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(300)

    /** "Sri Sai Motors, Pvt." and "SRI SAI MOTORS" are the same merchant. */
    fun merchantKey(merchant: String): String =
        merchant.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    /** What the owner or AI already decided for this merchant: a category name, [NOT_CAR], or null when unknown. */
    fun known(data: OwnerCarData, merchant: String?): String? =
        merchant?.let { data.merchantCategories[merchantKey(it)] }
}
