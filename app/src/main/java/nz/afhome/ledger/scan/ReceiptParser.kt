package nz.afhome.ledger.scan

import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.HomeRoom
import nz.afhome.ledger.data.normalizeName
import java.time.LocalDate
import java.time.Month

/**
 * Turns OCR rows from a New Zealand receipt into a [ReceiptDraft].
 *
 * Handles the common layouts of Woolworths, PAK'nSAVE, New World, fuel stations and general retail:
 * `NAME ..... 4.29`, quantity lines like `2 @ $2.50   5.00` (before or after the name),
 * weighed produce `0.842 kg @ $3.99/kg`, discount lines, and name/price split across rows.
 */
object ReceiptParser {

    private val PRICE_END = Regex("""(-)?\s*\$?\s?(\d{1,4}[.,]\d{2})\s*(-)?\s*[A-Za-z*#^]{0,2}$""")
    private val QTY = Regex(
        """^(\d+(?:[.,]\d+)?)\s*(kg|g|ea|l)?\s*(?:@|x|×)\s*\$?\s*(\d+[.,]\d{2,3})\s*(?:/\s*(kg|ea|l))?""",
        RegexOption.IGNORE_CASE,
    )
    private val LITRES = Regex("""(\d{1,3}[.,]\d{1,3})\s*(?:l|ltr|ltrs|litres?|liters?)\b""", RegexOption.IGNORE_CASE)
    private val PER_LITRE = Regex("""(?:@\s*\$?\s*(\d[.,]\d{2,3})|(\d[.,]\d{2,3})\s*(?:/\s*l|per\s*l|\$/l))""", RegexOption.IGNORE_CASE)
    private val PER_LITRE_CENTS = Regex("""(\d{3}[.,]\d)\s*c\s*/?\s*l""", RegexOption.IGNORE_CASE)
    private val ODO = Regex("""(?:odo|odometer|km)\D{0,3}(\d{4,6})""", RegexOption.IGNORE_CASE)

    private val DATE_NUM = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
    private val DATE_ISO = Regex("""\b(20\d{2})[/.\-](\d{1,2})[/.\-](\d{1,2})\b""")
    private val DATE_TEXT = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?[\s\-]*(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[\s\-,]*(\d{2,4})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val SKIP = listOf(
        "subtotal", "sub total", "sub-total", "total", "gst", "eftpos", "visa", "mastercard", "master card", "amex", "debit", "credit",
        "change", "cash", "balance", "rounding", "tax invoice", "invoice", "gst no", "gst#", "thank", "tel", "phone", "www",
        "approved", "account", "auth", "ref", "terminal", "rewards", "points", "clubcard", "onecard", "number of items",
        "items purchased", "you saved", "savings", "tendered", "amount due", "paywave", "contactless", "surcharge", "merchant",
        "receipt", "store no", "lane", "operator", "cashier", "opening hours", "abn", "customer copy",
    )
    private val DISCOUNT = listOf("discount", "saving", "promo", "club deal", "clubdeal", "special", "less", "off", "multibuy", "extra low")
    private fun wordsRegex(words: List<String>) =
        Regex("(?<![a-z])(" + words.joinToString("|") { Regex.escape(it.trim()) } + ")(?![a-z])")
    private val SKIP_RE = wordsRegex(SKIP)
    private val DISCOUNT_RE = wordsRegex(DISCOUNT)
    private val TOTAL_WORDS = listOf("total", "balance due", "amount due", "to pay", "total due", "total nzd", "total incl")

    /** Canonical display names for common Auckland stores. */
    private val STORES = listOf(
        "woolworths" to "Woolworths", "countdown" to "Woolworths", "pak n save" to "PAK'nSAVE", "paknsave" to "PAK'nSAVE",
        "pak'nsave" to "PAK'nSAVE", "pak nsave" to "PAK'nSAVE", "new world" to "New World", "freshchoice" to "FreshChoice",
        "fresh choice" to "FreshChoice", "supervalue" to "SuperValue", "four square" to "Four Square", "costco" to "Costco",
        "farro" to "Farro", "the warehouse" to "The Warehouse", "warehouse stationery" to "Warehouse Stationery", "kmart" to "Kmart",
        "bunnings" to "Bunnings", "mitre 10" to "Mitre 10", "chemist warehouse" to "Chemist Warehouse", "unichem" to "Unichem",
        "life pharmacy" to "Life Pharmacy", "bargain chemist" to "Bargain Chemist", "z energy" to "Z", "bp connect" to "BP",
        "mobil" to "Mobil", "gull" to "Gull", "waitomo" to "Waitomo", "caltex" to "Caltex", "noel leeming" to "Noel Leeming",
        "jb hi-fi" to "JB Hi-Fi", "jb hi fi" to "JB Hi-Fi", "harvey norman" to "Harvey Norman", "briscoes" to "Briscoes",
        "farmers" to "Farmers", "repco" to "Repco", "supercheap" to "Supercheap Auto", "tai ping" to "Tai Ping",
        "mcdonald" to "McDonald's", "kfc" to "KFC", "subway" to "Subway", "burger king" to "Burger King", "domino" to "Domino's",
        "spotlight" to "Spotlight", "animates" to "Animates", "rebel sport" to "Rebel Sport", "cotton on" to "Cotton On",
        "vtnz" to "VTNZ",
    )

    fun parse(rawRows: List<String>, categorizer: Categorizer, today: LocalDate = LocalDate.now()): ReceiptDraft {
        val rows = rawRows.map { it.replace('\t', ' ').replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val store = detectStore(rows)
        val date = rows.firstNotNullOfOrNull { findDate(it, today) }
        val total = findTotal(rows)
        val gst = rows.firstOrNull { it.lowercase().contains("gst") && !it.lowercase().contains("gst no") && !it.contains("#") }
            ?.let { PRICE_END.find(it)?.groupValues?.get(2)?.toMoney() }
            ?.takeIf { total == null || it < total }
        val payment = detectPayment(rows)

        val items = parseItems(rows, store, categorizer)
        val fuel = parseFuel(rows)
        val odo = rows.firstNotNullOfOrNull { ODO.find(it)?.groupValues?.get(1)?.toIntOrNull() }

        return ReceiptDraft(
            store = store,
            date = date?.toEpochDay(),
            total = total,
            gst = gst ?: total?.let { round2(it * 3.0 / 23.0) }, // NZ GST is 15% inclusive → 3/23 of the total
            paymentHint = payment,
            account = if (payment == "Cash") nz.afhome.ledger.data.PayAccount.CASH else null,
            items = items,
            fuelLitres = fuel?.first,
            fuelPricePerL = fuel?.second,
            odometer = odo,
            rawText = rows.joinToString("\n"),
        )
    }

    // ---------- header ----------

    fun detectStore(rows: List<String>): String {
        val head = rows.take(10).joinToString(" ").lowercase()
        STORES.firstOrNull { head.contains(it.first) }?.let { return it.second }
        // Otherwise: the first "wordy" line near the top that isn't boilerplate.
        return rows.take(6).firstOrNull { r ->
            val letters = r.count { it.isLetter() }
            letters >= 3 && letters.toDouble() / r.length > 0.6 &&
                listOf("tax invoice", "receipt", "welcome", "invoice", "gst").none { r.lowercase().contains(it) }
        }?.let { titleCase(it) } ?: ""
    }

    private fun titleCase(s: String) = s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    fun findDate(row: String, today: LocalDate): LocalDate? {
        DATE_ISO.find(row)?.let { m ->
            valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), today)?.let { return it }
        }
        DATE_NUM.find(row)?.let { m ->
            // NZ order is day/month/year.
            valid(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt(), today)?.let { return it }
        }
        DATE_TEXT.find(row)?.let { m ->
            val month = monthOf(m.groupValues[2]) ?: return@let
            valid(year(m.groupValues[3]), month, m.groupValues[1].toInt(), today)?.let { return it }
        }
        return null
    }

    private fun year(s: String): Int = s.toInt().let { if (it < 100) 2000 + it else it }

    private fun monthOf(s: String): Int? = Month.entries.firstOrNull { it.name.startsWith(s.take(3).uppercase()) }?.value

    private fun valid(y: Int, m: Int, d: Int, today: LocalDate): LocalDate? = runCatching {
        LocalDate.of(y, m, d).takeIf { !it.isAfter(today.plusDays(1)) && it.year >= 2015 }
    }.getOrNull()

    private fun findTotal(rows: List<String>): Double? {
        fun amountOf(r: String) = PRICE_END.find(r)?.groupValues?.get(2)?.toMoney()
        for (r in rows) {
            val l = r.lowercase()
            if (TOTAL_WORDS.any { l.contains(it) } && listOf("sub", "saving", "items", "gst", "points", "qty").none { l.contains(it) }) {
                amountOf(r)?.let { return it }
            }
        }
        // Fallback: the amount charged to the card.
        for (r in rows) {
            val l = r.lowercase()
            if (listOf("eftpos", "visa", "mastercard", "paywave", "credit", "debit", "amount").any { l.contains(it) }) {
                amountOf(r)?.let { return it }
            }
        }
        return null
    }

    private fun detectPayment(rows: List<String>): String? {
        val text = rows.joinToString(" ").lowercase()
        return listOf("eftpos" to "EFTPOS", "visa" to "Visa", "mastercard" to "Mastercard", "amex" to "Amex",
            "apple pay" to "Apple Pay", "google pay" to "Google Pay", "cash" to "Cash")
            .firstOrNull { text.contains(it.first) }?.second
    }

    // ---------- items ----------

    private class Building(var name: String, var qty: Double = 1.0, var unit: String? = null, var unitPrice: Double, var total: Double,
                           var doubtful: Boolean = false, var qtyApplied: Boolean = false)

    fun parseItems(rows: List<String>, store: String, categorizer: Categorizer): List<DraftItem> {
        val out = mutableListOf<Building>()
        var pendingName: String? = null
        var pendingQty: Triple<Double, String?, Double>? = null // qty, unit, unit price

        // Items end where the totals block starts.
        val end = rows.indexOfFirst { r ->
            val l = r.lowercase()
            (l.contains("subtotal") || l.contains("sub total") || l.startsWith("total") || l.contains(" total") ||
                l.contains("balance due")) && !l.contains("saving")
        }.let { if (it < 0) rows.size else it }
        // Items start after the date/header block if we can see it; otherwise from the top.
        val storeLower = store.lowercase()

        for (i in 0 until end) {
            val row = rows[i]
            val lower = row.lowercase()
            if (storeLower.isNotBlank() && lower.contains(storeLower) && i < 6) continue

            val qty = QTY.find(row)
            val priceMatch = PRICE_END.find(row)
            val price = priceMatch?.groupValues?.get(2)?.toMoney()
            val negative = priceMatch != null && (priceMatch.groupValues[1] == "-" || priceMatch.groupValues[3] == "-")
            val namePart = (if (priceMatch != null) row.substring(0, priceMatch.range.first) else row).trim()
            val letters = namePart.count { it.isLetter() }

            // Discounts / savings reduce the previous item.
            if (price != null && (negative || DISCOUNT_RE.containsMatchIn(lower)) && out.isNotEmpty() && qty == null) {
                val last = out.last()
                last.total = round2((last.total - price).coerceAtLeast(0.0))
                last.unitPrice = round2(last.total / last.qty)
                continue
            }
            if (SKIP_RE.containsMatchIn(lower)) { pendingName = null; continue }

            if (qty != null) {
                val q = qty.groupValues[1].toMoney()
                val unitPrice = qty.groupValues[3].toMoney()
                val unit = (qty.groupValues[2].ifEmpty { qty.groupValues[4] }).lowercase().ifEmpty { null }
                val computed = round2(q * unitPrice)
                val lineTotal = if (price != null && priceMatch!!.range.first > qty.range.last) price else computed
                val prev = out.lastOrNull()
                val restName = row.substring(qty.range.last + 1).let { PRICE_END.replace(it, "") }.trim()
                when {
                    // "2 @ $2.50  5.00" right after "BREAD 5.00" → quantity of the previous item.
                    prev != null && !prev.qtyApplied && kotlin.math.abs(prev.total - lineTotal) < 0.03 && pendingName == null -> {
                        prev.qty = q; prev.unit = unit; prev.unitPrice = unitPrice; prev.qtyApplied = true
                    }
                    // PAK'nSAVE style: name on one row, quantity + total on the next.
                    pendingName != null -> {
                        out += Building(pendingName, q, unit, unitPrice, lineTotal, qtyApplied = true)
                        pendingName = null
                    }
                    restName.count { it.isLetter() } >= 3 -> out += Building(restName, q, unit, unitPrice, lineTotal, qtyApplied = true)
                    else -> pendingQty = Triple(q, unit, unitPrice)
                }
                continue
            }

            if (price != null && letters >= 2) {
                val (q, unit, up) = pendingQty ?: Triple(1.0, null, price)
                pendingQty = null
                val name = cleanName(namePart)
                val garbled = letters.toDouble() / namePart.length.coerceAtLeast(1) < 0.5 || name.length < 3
                out += Building(name, q, unit, if (q == 1.0) price else up, price, doubtful = garbled || price > 500, qtyApplied = q != 1.0)
                pendingName = null
                continue
            }

            if (price != null && letters < 2) {
                // Price on its own row — pair it with a name we saw just before.
                pendingName?.let {
                    out += Building(it, unitPrice = price, total = price, doubtful = true)
                    pendingName = null
                }
                continue
            }

            if (letters >= 3 && i > 0) pendingName = cleanName(row)
        }

        return out.filter { it.total > 0 }.map { b ->
            val norm = normalizeName(b.name)
            val cat = categorizer.categorize(norm, store)
            DraftItem(
                name = b.name, qty = b.qty, unit = b.unit, unitPrice = round2(b.unitPrice), total = round2(b.total),
                category = cat, doubtful = b.doubtful, toInventory = cat.stocked, room = cat.defaultRoom,
                isGift = cat == Category.GIFTS,
            )
        }
    }

    private fun cleanName(s: String): String = s.trim().trimEnd('.', '-', '*', ':').replace(Regex("""^[#*\-\s]+"""), "").let {
        if (it.count { c -> c.isUpperCase() } > it.length / 2) titleCase(it) else it
    }

    // ---------- fuel ----------

    /** Returns litres and price-per-litre (dollars) if the receipt contains them. */
    fun parseFuel(rows: List<String>): Pair<Double, Double>? {
        var litres: Double? = null
        var ppl: Double? = null
        for (r in rows) {
            if (litres == null) LITRES.find(r)?.let { m -> m.groupValues[1].toMoney().takeIf { it in 1.0..200.0 }?.let { litres = it } }
            if (ppl == null) {
                PER_LITRE.find(r)?.let { m ->
                    (m.groupValues[1].ifEmpty { m.groupValues[2] }).toMoney().takeIf { it in 1.0..5.0 }?.let { ppl = it }
                }
                PER_LITRE_CENTS.find(r)?.let { m -> (m.groupValues[1].toMoney() / 100).takeIf { it in 1.0..5.0 }?.let { ppl = it } }
            }
        }
        val l = litres ?: return null
        return l to (ppl ?: 0.0)
    }

    private fun String.toMoney(): Double = replace(',', '.').toDouble()
    fun round2(v: Double) = Math.round(v * 100.0) / 100.0

    /** Used for manual entries and "single amount" receipts. */
    fun singleLine(store: String, total: Double, categorizer: Categorizer): DraftItem {
        val cat = Categorizer.storeCategory(store) ?: Category.OTHER
        return DraftItem(name = store.ifBlank { "Purchase" }, unitPrice = total, total = total, category = cat,
            toInventory = false, room = HomeRoom.OTHER)
    }
}
