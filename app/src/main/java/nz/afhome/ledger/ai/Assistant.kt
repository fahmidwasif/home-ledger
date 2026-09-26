package nz.afhome.ledger.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nz.afhome.ledger.analysis.Insights
import nz.afhome.ledger.analysis.Period
import nz.afhome.ledger.analysis.range
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.HomeRoom
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.Prefs
import nz.afhome.ledger.data.Repository
import nz.afhome.ledger.data.fmtDate
import nz.afhome.ledger.data.fmtShort
import nz.afhome.ledger.data.money0
import nz.afhome.ledger.data.normalizeName
import nz.afhome.ledger.data.qtyText
import nz.afhome.ledger.scan.Categorizer
import nz.afhome.ledger.scan.DraftItem
import nz.afhome.ledger.scan.ReceiptDraft
import java.time.LocalDate

/** Builds the household context the local model sees, and answers simple questions without the model. */
class Assistant(private val repo: Repository, private val prefs: Prefs, private val llm: LocalLlm) {

    private val intro = """
        You are the private household assistant for "Anika & Fahmid Home", a Bangladeshi couple living in Auckland, New Zealand.
        They own a car, usually pack lunch from home for work, enjoy Bangladeshi cooking, and sometimes buy gifts.
        Money is NZD; NZ GST is 15%. Payment accounts: ${PayAccount.entries.joinToString { it.label }}.
        Answer briefly and concretely using ONLY the data below. If the data doesn't contain the answer, say so.
        You run entirely on their phone; nothing leaves the device.
    """.trimIndent()

    suspend fun context(): String {
        val dao = repo.dao
        val receipts = dao.allReceipts()
        val items = dao.allItems()
        val today = LocalDate.now()
        val sb = StringBuilder(intro).append("\n\nToday is ${today.toEpochDay().fmtDate()}.\n")

        for (p in listOf(Period.THIS_MONTH, Period.LAST_MONTH)) {
            val r = Insights.build(p.range(today), receipts, items, dao.lunches(), dao.fuel(), dao.usage(), dao.budgets(), dao.vehicles(), prefs.weeklyGroceryTarget)
            sb.append("\n## ${p.label}: total ${money0(r.total)} over ${r.receiptCount} receipts\n")
            sb.append("By category: ").append(r.byCategory.take(8).joinToString { "${it.first.label} ${money0(it.second)}" }).append('\n')
            sb.append("By person: ").append(r.byPerson.joinToString { "${it.first.label} ${money0(it.second)}" }).append('\n')
            sb.append("By account: ").append(r.byAccount.joinToString { "${it.first} ${money0(it.second)}" }).append('\n')
            if (r.giftTotal > 0) sb.append("Gifts: ${money0(r.giftTotal)} ").append(r.giftsByRecipient.take(4).joinToString { "${it.label} ${money0(it.value)}" }).append('\n')
            if (r.packedLunches > 0) sb.append("Packed lunches: ${r.packedLunches}, saved ~${money0(r.lunchSavings)}\n")
            if (r.fuelTotal > 0) sb.append("Fuel: ${money0(r.fuelTotal)}\n")
        }

        sb.append("\n## Recent receipts\n")
        receipts.sortedByDescending { it.date }.take(12).forEach {
            sb.append("${it.date.fmtShort()} ${it.store} ${money0(it.total)} by ${Person.of(it.purchaser).label}\n")
        }

        val inv = dao.inventory().filter { it.quantity > 0 }
        if (inv.isNotEmpty()) {
            sb.append("\n## At home (item: qty, where)\n")
            inv.take(70).forEach { sb.append("${it.name}: ${qtyText(it.quantity)}, ${HomeRoom.of(it.room).label}${it.spot?.let { s -> " – $s" } ?: ""}\n") }
        }
        val shop = dao.shopping().filter { !it.done }
        if (shop.isNotEmpty()) sb.append("\n## Shopping list\n").append(shop.joinToString { it.name }).append('\n')
        dao.vehicles().forEach { v ->
            sb.append("\n## Car ${v.name}: WoF ${v.wofExpiry?.fmtDate() ?: "?"}, rego ${v.regoExpiry?.fmtDate() ?: "?"}, insurance renewal ${v.insuranceRenewal?.fmtDate() ?: "?"}\n")
        }
        return sb.toString()
    }

    /** Fast, exact answers for "where is X / do we have X" straight from the inventory. */
    suspend fun quickAnswer(question: String): String? {
        val q = question.lowercase()
        val wantsLocation = listOf("where", "do we have", "have we got", "how many", "how much .* left", "any ").any { Regex(it).containsMatchIn(q) }
        if (!wantsLocation) return null
        val stop = setOf("where", "is", "are", "the", "our", "my", "do", "we", "have", "got", "any", "how", "many", "much", "left", "some", "kept", "put", "a", "of")
        val words = normalizeName(q).split(' ').filter { it.length > 2 && it !in stop }
        if (words.isEmpty()) return null
        val hits = repo.dao.inventory().filter { item -> words.any { w -> item.normName.contains(w) } }
        if (hits.isEmpty()) return "I couldn't find \"${words.joinToString(" ")}\" in the home inventory."
        return hits.take(8).joinToString("\n") { i ->
            val where = HomeRoom.of(i.room).label + (i.spot?.let { " – $it" } ?: "")
            if (i.quantity > 0) "• ${i.name}: ${qtyText(i.quantity)}${i.unit?.let { " $it" } ?: ""} in $where"
            else "• ${i.name}: used up (usually kept in $where)"
        }
    }

    // ---------- receipt help ----------

    @Serializable
    private data class AiItem(val name: String = "", val qty: Double = 1.0, val price: Double = 0.0)

    @Serializable
    private data class AiReceipt(val store: String? = null, val date: String? = null, val total: Double? = null, val items: List<AiItem> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Asks the local model to re-read OCR text that the rule-based parser struggled with.
     * Only fills gaps: fields the parser already found are kept.
     */
    suspend fun refineReceipt(d: ReceiptDraft, categorizer: Categorizer): ReceiptDraft {
        val raw = d.rawText ?: return d
        val out = llm.ask(
            system = "You extract data from shop receipts in New Zealand. Reply with JSON only, no prose.",
            prompt = """
                Receipt text (from OCR, may contain errors):
                ---
                ${raw.take(3500)}
                ---
                Return JSON: {"store": string, "date": "YYYY-MM-DD", "total": number, "items": [{"name": string, "qty": number, "price": number}]}
                "price" is the line total paid for that item. Skip GST, subtotal, payment and discount lines.
            """.trimIndent(),
            temperature = 0.1,
        )
        val body = out.substringAfter('{', "").substringBeforeLast('}', "")
        if (body.isEmpty()) return d
        val ai = runCatching { json.decodeFromString(AiReceipt.serializer(), "{$body}") }.getOrNull() ?: return d
        val date = d.date ?: ai.date?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }
        val store = d.store.ifBlank { ai.store.orEmpty() }
        val items = if (d.items.size >= ai.items.size) d.items else ai.items.filter { it.name.isNotBlank() && it.price > 0 }.map {
            val cat = categorizer.categorize(normalizeName(it.name), store)
            DraftItem(name = it.name, qty = it.qty.coerceAtLeast(1.0), unitPrice = it.price / it.qty.coerceAtLeast(1.0), total = it.price,
                category = cat, toInventory = cat.stocked, room = cat.defaultRoom, doubtful = true)
        }
        return d.copy(store = store, date = date, total = d.total ?: ai.total, items = items)
    }

    /** Asks the model for a narrative "deep analysis" of spending habits. */
    suspend fun deepAnalysis(): String = llm.ask(
        system = context(),
        prompt = "Give a deep analysis of our spending habits: 5–8 short bullet points covering trends, who spends on what, " +
            "groceries vs Auckland norms, Bangladeshi groceries, gifts, car costs, packed lunches, and 3 practical suggestions to save money.",
        temperature = 0.5,
    )

    companion object {
        val SUGGESTED = listOf(
            "Where is the basmati rice?",
            "How much did we spend on groceries this month?",
            "Who spent more this month, Anika or Fahmid?",
            "What should we buy this week?",
            "How much have we spent on gifts?",
            "Give me a Bangladeshi dinner idea using what we have at home",
            "How can we cut our car costs?",
            "Which account did we use most this month?",
        )

        fun categoryNames() = Category.entries.joinToString { it.label }
    }
}
