package nz.afhome.ledger.scan

import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.HomeRoom
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person

/** An editable receipt that hasn't been saved yet: the output of OCR+parsing, or a blank manual entry. */
data class ReceiptDraft(
    val store: String = "",
    val date: Long? = null,
    val total: Double? = null,
    val gst: Double? = null,
    val purchaser: Person? = null,
    val account: PayAccount? = null,
    /** What the receipt says about the card type ("Visa", "EFTPOS"...), a hint for choosing the account. */
    val paymentHint: String? = null,
    val notes: String = "",
    val items: List<DraftItem> = emptyList(),
    val fuelLitres: Double? = null,
    val fuelPricePerL: Double? = null,
    val odometer: Int? = null,
    val rawText: String? = null,
    val manual: Boolean = false,
    /** Answers to clarification questions the user has dismissed ("the total is right", ...). */
    val acknowledged: Set<String> = emptySet(),
) {
    val itemsSum: Double get() = items.sumOf { it.total }
}

data class DraftItem(
    val key: Long = nextKey(),
    val name: String,
    val qty: Double = 1.0,
    val unit: String? = null,
    val unitPrice: Double,
    val total: Double,
    val category: Category = Category.OTHER,
    /** OCR looked shaky (garbled name, price inferred...). */
    val doubtful: Boolean = false,
    val toInventory: Boolean = category.stocked,
    val room: HomeRoom = category.defaultRoom,
    val isGift: Boolean = false,
    val giftFor: String = "",
    val giftOccasion: String = "",
) {
    companion object {
        private var counter = 0L
        fun nextKey(): Long = ++counter
    }
}

/** A question the app asks when it could not read something important on the receipt. */
data class Clarification(
    val id: String,
    val question: String,
    val kind: Kind,
    val itemKey: Long? = null,
    val blocking: Boolean = true,
) {
    enum class Kind { PURCHASER, ACCOUNT, STORE, DATE, TOTAL, MISMATCH, ITEM_NAME, ITEM_PRICE, NO_ITEMS, GIFT, FUEL }
}

object Clarifier {
    fun questions(d: ReceiptDraft): List<Clarification> {
        val q = mutableListOf<Clarification>()
        if (d.purchaser == null) q += Clarification("who", "Who made this purchase?", Clarification.Kind.PURCHASER)
        if (d.account == null) q += Clarification(
            "account",
            "Which account paid?" + (d.paymentHint?.let { " (receipt says: $it)" } ?: ""),
            Clarification.Kind.ACCOUNT,
        )
        if (d.store.isBlank()) q += Clarification("store", "I couldn't read the shop name. Where was this bought?", Clarification.Kind.STORE)
        if (d.date == null) q += Clarification("date", "I couldn't find the purchase date. When was it?", Clarification.Kind.DATE)
        if (d.total == null) q += Clarification("total", "I couldn't find the total. How much was paid in total?", Clarification.Kind.TOTAL)
        if (d.items.isEmpty() && !d.manual) q += Clarification(
            "noitems", "I couldn't pick out any items. Add them below, or save as a single-amount purchase.",
            Clarification.Kind.NO_ITEMS, blocking = false,
        )
        val total = d.total
        if (total != null && d.items.isNotEmpty() && "mismatch" !in d.acknowledged) {
            val diff = total - d.itemsSum
            if (kotlin.math.abs(diff) > 0.05) q += Clarification(
                "mismatch",
                "Items add up to ${nz.afhome.ledger.data.money(d.itemsSum)} but the total is ${nz.afhome.ledger.data.money(total)} " +
                    "(${if (diff > 0) "missing" else "extra"} ${nz.afhome.ledger.data.money(kotlin.math.abs(diff))}). Is a line missing or mis-read?",
                Clarification.Kind.MISMATCH,
            )
        }
        for (it in d.items) {
            if (it.doubtful && "item-${it.key}" !in d.acknowledged) q += Clarification(
                "item-${it.key}", "Is \"${it.name}\" (${nz.afhome.ledger.data.money(it.total)}) read correctly?",
                Clarification.Kind.ITEM_NAME, itemKey = it.key, blocking = false,
            )
        }
        if (Categorizer.isFuelStation(d.store) && d.fuelLitres == null && "fuel" !in d.acknowledged) q += Clarification(
            "fuel", "This looks like a fuel receipt. How many litres did you fill?", Clarification.Kind.FUEL, blocking = false,
        )
        return q
    }
}
