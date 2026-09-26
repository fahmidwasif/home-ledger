package nz.afhome.ledger.analysis

import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.InventoryItem
import nz.afhome.ledger.data.LineItem
import nz.afhome.ledger.data.Receipt

/**
 * Predicts what needs buying from purchase rhythm: if milk is bought every ~6 days and it's been 6,
 * milk is "usually due", unless the inventory says there's still plenty at home.
 */
object Predictor {

    data class Suggestion(
        val name: String,
        val normName: String,
        val category: Category,
        val intervalDays: Int,
        val dueDay: Long,
        val lastPrice: Double?,
        val cheapestStore: String?,
        val timesBought: Int,
    )

    fun rhythm(items: List<LineItem>, receipts: Map<Long, Receipt>): List<Suggestion> {
        return items.asSequence()
            .filter { !it.isGift && Category.of(it.category).stocked }
            .groupBy { it.normName }
            .mapNotNull { (norm, lines) ->
                val withDates = lines.mapNotNull { l -> receipts[l.receiptId]?.let { it to l } }.sortedBy { it.first.date }
                val days = withDates.map { it.first.date }.distinct()
                if (days.size < 2) return@mapNotNull null
                val gaps = days.zipWithNext { a, b -> (b - a).toInt() }.filter { it > 0 }.sorted()
                if (gaps.isEmpty()) return@mapNotNull null
                val interval = gaps[gaps.size / 2].coerceAtLeast(1)
                val last = withDates.last()
                Suggestion(
                    name = last.second.name, normName = norm, category = Category.of(last.second.category),
                    intervalDays = interval, dueDay = days.last() + interval, lastPrice = last.second.unitPrice,
                    cheapestStore = withDates.minByOrNull { it.second.unitPrice }?.first?.store, timesBought = days.size,
                )
            }
    }

    fun dueItems(items: List<LineItem>, receipts: Map<Long, Receipt>, inventory: List<InventoryItem>, today: Long): List<Suggestion> {
        val stock = inventory.associateBy { it.normName }
        return rhythm(items, receipts).filter { s ->
            val inv = stock[s.normName]
            val plentyLeft = inv != null && inv.quantity > inv.lowThreshold + 1
            // Stop suggesting things that clearly aren't bought any more.
            today >= s.dueDay - 2 && !plentyLeft && today - s.dueDay < s.intervalDays * 4
        }.sortedBy { it.dueDay }
    }
}
