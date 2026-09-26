package nz.afhome.ledger.analysis

import nz.afhome.ledger.data.Budget
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.CategoryGroup
import nz.afhome.ledger.data.FuelLog
import nz.afhome.ledger.data.LineItem
import nz.afhome.ledger.data.LunchLog
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.Receipt
import nz.afhome.ledger.data.UsageEvent
import nz.afhome.ledger.data.UsePurpose
import nz.afhome.ledger.data.Vehicle
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.money0
import nz.afhome.ledger.data.toDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Auckland reference figures (researched September 2026) used for comparisons and tips. */
object Auckland {
    const val COUPLE_GROCERY_LOW = 160.0   // $/week, couple, Auckland ~5–10% above the NZ average
    const val COUPLE_GROCERY_HIGH = 220.0
    const val REGO_PETROL_YEAR = 181.45    // Private petrol car licence incl. ACC levy, from 1 July 2026
    const val WOF_TYPICAL = 85.0           // AA / VTNZ non-member price range ~$76–91
    const val RUC_PER_1000KM = 76.0        // Diesel / EV road user charges
    const val PETROL_TYPICAL = 3.00        // $/L 91, mid-2026 (volatile)
}

enum class Period(val label: String) { THIS_MONTH("This month"), LAST_MONTH("Last month"), THREE_MONTHS("3 months"), THIS_YEAR("This year"), ALL("All time") }

data class Range(val from: Long, val to: Long) {
    val days: Long get() = (to - from + 1).coerceAtLeast(1)
    operator fun contains(d: Long) = d in from..to
}

fun Period.range(today: LocalDate = LocalDate.now(), earliest: Long? = null): Range {
    val ym = YearMonth.from(today)
    return when (this) {
        Period.THIS_MONTH -> Range(ym.atDay(1).toEpochDay(), today.toEpochDay())
        Period.LAST_MONTH -> ym.minusMonths(1).let { Range(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
        Period.THREE_MONTHS -> Range(ym.minusMonths(2).atDay(1).toEpochDay(), today.toEpochDay())
        Period.THIS_YEAR -> Range(LocalDate.of(today.year, 1, 1).toEpochDay(), today.toEpochDay())
        Period.ALL -> Range(earliest ?: today.toEpochDay(), today.toEpochDay())
    }
}

data class Amount(val label: String, val value: Double, val extra: String? = null)

data class PriceChange(val name: String, val first: Double, val last: Double, val firstDay: Long, val lastDay: Long) {
    val pct: Double get() = if (first > 0) (last - first) / first * 100 else 0.0
}

data class InsightReport(
    val range: Range,
    val total: Double,
    val previousTotal: Double,
    val receiptCount: Int,
    val byCategory: List<Pair<Category, Double>>,
    val byGroup: List<Pair<CategoryGroup, Double>>,
    val byPerson: List<Pair<Person, Double>>,
    val personTopCategories: Map<Person, List<Pair<Category, Double>>>,
    val byAccount: List<Pair<String, Double>>,
    val byStore: List<Amount>,
    val monthly: List<Pair<YearMonth, Double>>,
    val monthlyGroceries: List<Pair<YearMonth, Double>>,
    val weeklyGroceries: Double,
    val trackedDays: Long,
    val deshiTotal: Double,
    val deshiShare: Double,
    val deshiTopItems: List<Amount>,
    val deshiStores: List<Amount>,
    val giftTotal: Double,
    val giftsByRecipient: List<Amount>,
    val giftsByOccasion: List<Amount>,
    val givingTotal: Double,
    val eatingOut: Double,
    val boughtLunch: Double,
    val packedLunches: Int,
    val packedLunchesByPerson: List<Pair<Person, Int>>,
    val lunchSavings: Double,
    val fuelTotal: Double,
    val fuelLitres: Double,
    val avgPricePerL: Double?,
    val litresPer100km: Double?,
    val carTotal: Double,
    val carYearProjection: Double,
    val weekday: List<Pair<DayOfWeek, Double>>,
    val topUpCount: Int,
    val topUpTotal: Double,
    val priceRises: List<PriceChange>,
    val topItems: List<Amount>,
    val wastedValue: Double,
    val wastedItems: List<Amount>,
    val budgets: List<Triple<Category, Double, Double>>, // category, spent, budget (monthly, scaled to range)
    val tips: List<String>,
)

object Insights {

    fun build(
        range: Range,
        receipts: List<Receipt>,
        items: List<LineItem>,
        lunches: List<LunchLog>,
        fuel: List<FuelLog>,
        usage: List<UsageEvent>,
        budgets: List<Budget>,
        vehicles: List<Vehicle>,
        weeklyTarget: Double,
    ): InsightReport {
        val receiptById = receipts.associateBy { it.id }
        val inRange = receipts.filter { it.date in range }
        val ids = inRange.map { it.id }.toSet()
        val lines = items.filter { it.receiptId in ids }
        val prevRange = Range(range.from - range.days, range.from - 1)
        val previousTotal = receipts.filter { it.date in prevRange }.sumOf { it.total }
        val total = inRange.sumOf { it.total }

        // Receipts saved without item lines still count, under "Other".
        val itemised = lines.groupBy { it.receiptId }
        val unitemised = inRange.filter { itemised[it.id].isNullOrEmpty() }

        val byCategory = (lines.map { Category.of(it.category) to it.total } + unitemised.map { Category.OTHER to it.total })
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
        val catMap = byCategory.toMap()
        fun cat(vararg c: Category) = c.sumOf { catMap[it] ?: 0.0 }

        val byGroup = byCategory.groupBy({ it.first.group }, { it.second }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

        val byPerson = Person.entries.map { p -> p to inRange.filter { it.purchaser == p.name }.sumOf { it.total } }.filter { it.second > 0 }
        val personTop = Person.entries.associateWith { p ->
            val pIds = inRange.filter { it.purchaser == p.name }.map { it.id }.toSet()
            lines.filter { it.receiptId in pIds }.groupBy { Category.of(it.category) }.mapValues { e -> e.value.sumOf { it.total } }
                .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
        }
        val byAccount = inRange.groupBy { PayAccount.of(it.payment)?.label ?: "Not recorded" }
            .mapValues { e -> e.value.sumOf { it.total } }.entries.sortedByDescending { it.value }.map { it.key to it.value }

        val byStore = inRange.groupBy { it.store }.map { (s, rs) -> Amount(s, rs.sumOf { it.total }, "${rs.size} visits") }
            .sortedByDescending { it.value }.take(8)

        // 12-month trend, independent of the selected period.
        val thisMonth = YearMonth.now()
        val months = (11 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
        fun monthOf(r: Receipt) = YearMonth.from(r.date.toDate())
        val monthly = months.map { m -> m to receipts.filter { monthOf(it) == m }.sumOf { it.total } }
        val monthlyGroceries = months.map { m ->
            val mIds = receipts.filter { monthOf(it) == m }.map { it.id }.toSet()
            m to items.filter { it.receiptId in mIds && Category.of(it.category).group == CategoryGroup.GROCERY }.sumOf { it.total }
        }

        val groceryLines = lines.filter { Category.of(it.category).group == CategoryGroup.GROCERY }
        val groceryTotal = groceryLines.sumOf { it.total }
        // Only count the weeks since tracking actually started, otherwise a new user looks thrifty.
        val firstDay = maxOf(range.from, receipts.minOfOrNull { it.date } ?: range.from)
        val trackedDays = (range.to - firstDay + 1).coerceAtLeast(1)
        val weeks = (trackedDays / 7.0).coerceAtLeast(1.0)
        val weeklyGroceries = groceryTotal / weeks

        val deshiLines = lines.filter { it.category == Category.DESHI.name }
        val deshiTotal = deshiLines.sumOf { it.total }
        val deshiTopItems = deshiLines.groupBy { it.normName }.map { (_, l) -> Amount(l.first().name, l.sumOf { it.total }, "×${l.size}") }
            .sortedByDescending { it.value }.take(6)
        val deshiStores = deshiLines.groupBy { receiptById[it.receiptId]?.store ?: "?" }.map { (s, l) -> Amount(s, l.sumOf { it.total }) }
            .sortedByDescending { it.value }.take(5)

        val giftLines = lines.filter { it.isGift || it.category == Category.GIFTS.name }
        val giftTotal = giftLines.sumOf { it.total }
        val giftsByRecipient = giftLines.groupBy { it.giftFor?.trim()?.ifEmpty { null } ?: "Unspecified" }
            .map { (k, l) -> Amount(k, l.sumOf { it.total }, "${l.size} gifts") }.sortedByDescending { it.value }
        val giftsByOccasion = giftLines.groupBy { it.giftOccasion?.ifEmpty { null } ?: "Unspecified" }
            .map { (k, l) -> Amount(k, l.sumOf { it.total }) }.sortedByDescending { it.value }

        val rangeLunches = lunches.filter { it.date in range }
        val boughtLunch = cat(Category.WORK_LUNCH)
        val lunchSavings = rangeLunches.sumOf { it.boughtCost - it.packedCost }

        val rangeFuel = fuel.filter { it.date in range }
        val fuelTotal = rangeFuel.sumOf { it.total }.takeIf { it > 0 } ?: cat(Category.FUEL)
        val litres = rangeFuel.sumOf { it.litres }
        val avgPpl = if (litres > 0) rangeFuel.sumOf { it.pricePerL * it.litres } / litres else null
        val lp100 = consumption(fuel)
        val carTotal = cat(Category.FUEL, Category.CAR_CARE, Category.CAR_ADMIN, Category.PARKING)
        val fuelPerYear = if (range.days >= 28) fuelTotal / range.days * 365 else fuelTotal * 12
        val insurance = vehicles.sumOf { it.insuranceAnnual ?: 0.0 }
        val carYear = fuelPerYear + (Auckland.REGO_PETROL_YEAR + Auckland.WOF_TYPICAL) * vehicles.size.coerceAtLeast(1) + insurance

        val weekday = DayOfWeek.entries.map { d -> d to inRange.filter { it.date.toDate().dayOfWeek == d }.sumOf { it.total } }

        val groceryReceipts = inRange.filter { r -> itemised[r.id].orEmpty().any { Category.of(it.category).group == CategoryGroup.GROCERY } }
        val topUps = groceryReceipts.filter { it.total < 30 }

        val priceRises = items.filter { !it.isGift && it.unitPrice > 0 }.groupBy { it.normName }.mapNotNull { (_, l) ->
            val dated = l.mapNotNull { li -> receiptById[li.receiptId]?.let { it.date to li } }.sortedBy { it.first }
            if (dated.size < 2 || dated.first().first == dated.last().first) return@mapNotNull null
            val a = dated.first(); val b = dated.last()
            PriceChange(b.second.name, a.second.unitPrice, b.second.unitPrice, a.first, b.first)
        }.filter { abs(it.pct) >= 8 }.sortedByDescending { it.pct }.take(10)

        val topItems = lines.groupBy { it.normName }.map { (_, l) -> Amount(l.first().name, l.sumOf { it.total }, "×${l.size}") }
            .sortedByDescending { it.value }.take(10)

        val wasted = usage.filter { it.date in range && it.purpose == UsePurpose.WASTED.name }
        val wastedValue = wasted.sumOf { it.value ?: 0.0 }
        val wastedItems = wasted.groupBy { it.itemName }.map { (k, l) -> Amount(k, l.sumOf { it.value ?: 0.0 }, "×${l.size}") }
            .sortedByDescending { it.value }.take(5)

        val monthsInRange = range.days / 30.4
        val budgetRows = budgets.map { b -> Triple(Category.of(b.category), catMap[Category.of(b.category)] ?: 0.0, b.monthly * monthsInRange.coerceAtLeast(1.0)) }

        val report = InsightReport(
            range, total, previousTotal, inRange.size, byCategory, byGroup, byPerson, personTop, byAccount, byStore,
            monthly, monthlyGroceries, weeklyGroceries, trackedDays, deshiTotal, if (groceryTotal > 0) deshiTotal / groceryTotal else 0.0,
            deshiTopItems, deshiStores, giftTotal, giftsByRecipient, giftsByOccasion, cat(Category.CHARITY, Category.FAMILY_SUPPORT),
            cat(Category.DINING), boughtLunch, rangeLunches.size,
            Person.entries.map { p -> p to rangeLunches.count { it.person == p.name } }.filter { it.second > 0 },
            lunchSavings, fuelTotal, litres, avgPpl, lp100, carTotal, carYear, weekday, topUps.size, topUps.sumOf { it.total },
            priceRises, topItems, wastedValue, wastedItems, budgetRows, emptyList(),
        )
        return report.copy(tips = tips(report, weeklyTarget))
    }

    /** L/100km from consecutive fills with odometer readings (full-tank method). */
    private fun consumption(fuel: List<FuelLog>): Double? {
        val withOdo = fuel.filter { it.odometer != null }.sortedBy { it.odometer }
        if (withOdo.size < 2) return null
        val km = (withOdo.last().odometer!! - withOdo.first().odometer!!).toDouble()
        val litres = withOdo.drop(1).sumOf { it.litres }
        return if (km > 50) litres / km * 100 else null
    }

    private fun tips(r: InsightReport, weeklyTarget: Double): List<String> {
        val t = mutableListOf<String>()
        if (r.receiptCount == 0) return listOf("Scan a few receipts and insights will appear here.")

        if (r.previousTotal > 0) {
            val ch = (r.total - r.previousTotal) / r.previousTotal * 100
            if (abs(ch) >= 10) t += "Spending is ${if (ch > 0) "up" else "down"} ${abs(ch).toInt()}% on the previous period (${money0(r.previousTotal)} → ${money0(r.total)})."
        }
        if (r.weeklyGroceries > 0 && r.trackedDays < 14) {
            t += "Only ${r.trackedDays} day${if (r.trackedDays == 1L) "" else "s"} of receipts so far. Weekly comparisons with Auckland norms get meaningful after about two weeks."
        } else if (r.weeklyGroceries > 0) {
            val w = r.weeklyGroceries
            t += when {
                w > Auckland.COUPLE_GROCERY_HIGH -> "Groceries average ${money0(w)}/week, above the typical Auckland couple range of \$160–220. Doing the main shop at PAK'nSAVE is usually cheapest; New World only pays off with a Clubcard."
                w < Auckland.COUPLE_GROCERY_LOW -> "Groceries average ${money0(w)}/week, below the typical Auckland couple range (\$160–220). Nice work."
                else -> "Groceries average ${money0(w)}/week, inside the typical Auckland couple range (\$160–220)."
            }
            if (w > weeklyTarget) t += "That's ${money0(w - weeklyTarget)}/week over your ${money0(weeklyTarget)} target."
        }
        if (r.topUpCount >= 4) t += "${r.topUpCount} small top-up shops (under \$30) added up to ${money0(r.topUpTotal)}. Top-up trips tend to add impulse buys, and the predicted shopping list can help plan one bigger weekly shop."
        if (r.deshiShare > 0.05) t += "Bangladeshi & South Asian groceries are ${(r.deshiShare * 100).toInt()}% of grocery spend (${money0(r.deshiTotal)}). Buying rice, dal and spices in bulk sacks is usually much cheaper per kg."
        if (r.packedLunches > 0) t += "${r.packedLunches} packed lunch${if (r.packedLunches == 1) "" else "es"} saved about ${money0(r.lunchSavings)} compared with buying lunch at work."
        if (r.boughtLunch > 0 && r.packedLunches == 0) t += "${money0(r.boughtLunch)} went on bought lunches at work. Each packed lunch saves roughly \$15."
        if (r.eatingOut > 0 && r.total > 0 && r.eatingOut / r.total > 0.12) t += "Eating out & takeaway is ${(r.eatingOut / r.total * 100).toInt()}% of spending (${money0(r.eatingOut)})."
        r.avgPricePerL?.let { p ->
            t += "Fuel averaged ${money(p)}/L. Compare pump prices with the Gaspy app. Supermarket fuel discounts and unmanned stations (Gull, Waitomo, NPD, Costco) are often cheaper in Auckland."
        }
        r.priceRises.firstOrNull { it.pct > 15 }?.let { t += "\"${it.name}\" went from ${money(it.first)} to ${money(it.last)} (+${it.pct.toInt()}%). Worth checking another store or brand." }
        if (r.wastedValue > 5) t += "About ${money0(r.wastedValue)} of food was thrown out. Check the \"expiring soon\" list on Home."
        val anika = r.byPerson.firstOrNull { it.first == Person.ANIKA }?.second ?: 0.0
        val fahmid = r.byPerson.firstOrNull { it.first == Person.FAHMID }?.second ?: 0.0
        if (anika + fahmid > 0) t += "Anika paid ${money0(anika)} and Fahmid ${money0(fahmid)} of purchases this period."
        r.budgets.filter { it.second > it.third }.forEach { (c, spent, b) -> t += "Over budget on ${c.label}: ${money0(spent)} of ${money0(b)}." }
        if (r.giftTotal > 0) t += "Gifts this period: ${money0(r.giftTotal)}" +
            (r.giftsByOccasion.firstOrNull { it.label != "Unspecified" }?.let { ", mostly for ${it.label}." } ?: ".")
        return t
    }

    /** Days until a date, for reminders. */
    fun daysUntil(epochDay: Long): Long = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(epochDay))
}
