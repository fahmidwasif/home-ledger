package nz.afhome.ledger.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nz.afhome.ledger.analysis.Insights
import nz.afhome.ledger.analysis.Period
import nz.afhome.ledger.analysis.range
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.Frequency
import nz.afhome.ledger.data.HomeRoom
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
        You are "the Owl", the private household assistant for Anika & Fahmid, a Bangladeshi couple living in Auckland, New Zealand.
        They own a car, usually pack lunch from home for work, love Bangladeshi cooking, and sometimes buy gifts. Money is NZD.
        Rules: facts about THEIR household come only from the data below; never invent amounts, dates, people or counts.
        If the data doesn't answer a household question, say so briefly. For recipes, tips and general questions, use your own knowledge.
        Keep answers short and practical (a few sentences or a short list).
    """.trimIndent()

    /** What a question is about, so the model only sees relevant data (small models get confused by too much). */
    enum class Topic { COOKING, STOCK, SHOPPING, SPENDING, CAR, GIFTS, SUBSCRIPTIONS, LUNCH, GENERAL }

    fun topics(question: String): Set<Topic> {
        val q = " " + question.lowercase() + " "
        fun has(vararg w: String) = w.any { q.contains(it) }
        val t = mutableSetOf<Topic>()
        if (has("cook", "recipe", "dinner", "breakfast", "meal", "curry", "bhat", "bhaji", "bhorta", "make with", " eat", "dish", "biryani", "khichuri", "tiffin")) t += Topic.COOKING
        if (has("where", "do we have", "have we got", "stock", "pantry", "fridge", "freezer", " left", "run out", "expir")) t += Topic.STOCK
        if (has(" buy", "shopping", "grocery list", "need to get", "shop for", "this week")) t += Topic.SHOPPING
        if (has("spend", "spent", "cost", "money", "budget", " save", "saving", "expens", "paid", " pay ", "account", " anz", " asb", "cash", " who ", "more than", "month", "trend", "habit", "analys", "$")) t += Topic.SPENDING
        if (has(" car", "fuel", "petrol", "wof", "rego", "insurance", "service", "parking")) t += Topic.CAR
        if (has("gift", "present", " eid", "birthday", "wedding")) t += Topic.GIFTS
        if (has("subscription", "netflix", "spotify", "recurring", "monthly bill", "renew")) t += Topic.SUBSCRIPTIONS
        if (has("lunch", "lunchbox", "packed")) t += Topic.LUNCH
        if (t.isEmpty()) t += Topic.GENERAL
        return t
    }

    /** Household data relevant to [question]. With no question, a broad summary (used for deep analysis). */
    suspend fun context(question: String? = null): String {
        val dao = repo.dao
        val today = LocalDate.now()
        val t = question?.let(::topics)
            ?: setOf(Topic.SPENDING, Topic.GIFTS, Topic.CAR, Topic.LUNCH, Topic.SUBSCRIPTIONS, Topic.SHOPPING)
        val sb = StringBuilder(intro)
        sb.appendLine().appendLine().appendLine("Today is ${today.toEpochDay().fmtDate()}.")

        if (Topic.COOKING in t || Topic.STOCK in t || Topic.SHOPPING in t || Topic.GENERAL in t) {
            val inv = dao.inventory().filter { it.quantity > 0 }
            sb.appendLine().appendLine("## Food and items at home now (name: quantity, where)")
            if (inv.isEmpty()) sb.appendLine("(nothing recorded yet)")
            inv.take(if (Topic.GENERAL in t) 30 else 80).forEach {
                val unit = it.unit?.let { u -> " $u" } ?: ""
                val spot = it.spot?.let { s -> " – $s" } ?: ""
                sb.appendLine("${it.name}: ${qtyText(it.quantity)}$unit, ${HomeRoom.of(it.room).label}$spot")
            }
            if (Topic.COOKING in t) sb.appendLine("When suggesting meals, prefer dishes that use what they already have.")
        }
        if (Topic.SHOPPING in t) {
            val shop = dao.shopping().filter { !it.done }
            sb.appendLine().appendLine("## Shopping list")
            sb.appendLine(if (shop.isEmpty()) "(empty)" else shop.joinToString { it.name })
        }
        if (Topic.SPENDING in t || Topic.GIFTS in t || Topic.LUNCH in t || Topic.CAR in t) {
            val receipts = dao.allReceipts()
            val items = dao.allItems()
            for (p in listOf(Period.THIS_MONTH, Period.LAST_MONTH)) {
                val r = Insights.build(
                    p.range(today), receipts, items, dao.lunches(), dao.fuel(), dao.usage(), dao.budgets(), dao.vehicles(),
                    prefs.weeklyGroceryTarget, dao.subscriptions(),
                )
                sb.appendLine().appendLine("## ${p.label}: total spent ${money0(r.total)} (${r.receiptCount} purchases)")
                if (Topic.SPENDING in t) {
                    if (r.byCategory.isNotEmpty()) sb.appendLine("By category: " + r.byCategory.take(10).joinToString { "${it.first.label} ${money0(it.second)}" })
                    if (r.byPerson.isNotEmpty()) sb.appendLine("Paid by: " + r.byPerson.joinToString { "${it.first.label} ${money0(it.second)}" })
                    if (r.byAccount.isNotEmpty()) sb.appendLine("From accounts: " + r.byAccount.joinToString { "${it.first} ${money0(it.second)}" })
                    if (r.byStore.isNotEmpty()) sb.appendLine("Shops: " + r.byStore.take(5).joinToString { "${it.label} ${money0(it.value)}" })
                }
                if (Topic.GIFTS in t) {
                    val who = r.giftsByRecipient.take(5).joinToString { "${it.label} ${money0(it.value)}" }
                    sb.appendLine("Gifts: ${money0(r.giftTotal)}" + if (who.isNotEmpty()) " ($who)" else "")
                }
                if (Topic.LUNCH in t) sb.appendLine("Packed lunches: ${r.packedLunches} (saved about ${money0(r.lunchSavings)}); bought lunches ${money0(r.boughtLunch)}")
                if (Topic.CAR in t) sb.appendLine("Car costs: ${money0(r.carTotal)} (fuel ${money0(r.fuelTotal)})")
            }
            if (Topic.SPENDING in t) {
                sb.appendLine().appendLine("## Latest purchases")
                receipts.sortedByDescending { it.date }.take(10).forEach {
                    sb.appendLine("${it.date.fmtShort()}: ${it.store} ${money0(it.total)}, by ${Person.of(it.purchaser).label}")
                }
            }
        }
        if (Topic.SUBSCRIPTIONS in t || question == null) {
            val subs = dao.subscriptions().filter { it.active }
            if (subs.isNotEmpty() || Topic.SUBSCRIPTIONS in t) {
                sb.appendLine().appendLine("## Recurring payments")
                if (subs.isEmpty()) sb.appendLine("(none recorded)")
                subs.forEach {
                    sb.appendLine("${it.name}: ${money0(it.amount)} ${Frequency.of(it.frequency).label.lowercase()}, next ${it.nextDue.fmtDate()}")
                }
            }
        }
        if (Topic.CAR in t) dao.vehicles().forEach { v ->
            sb.appendLine().appendLine(
                "## Car ${v.name}: WoF due ${v.wofExpiry?.fmtDate() ?: "not set"}, rego due ${v.regoExpiry?.fmtDate() ?: "not set"}, " +
                    "insurance renews ${v.insuranceRenewal?.fmtDate() ?: "not set"}"
            )
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
            "groceries vs Auckland norms, Bangladeshi groceries, gifts, car costs, subscriptions, packed lunches, " +
            "and 3 practical suggestions to save money. Use only the numbers given.",
        temperature = 0.5,
    )

    companion object {
        val SUGGESTED = listOf(
            "Give me a Bangladeshi dinner idea using what we have at home",
            "Where is the basmati rice?",
            "How much did we spend on groceries this month?",
            "Who spent more this month, Anika or Fahmid?",
            "What should we buy this week?",
            "How much do our subscriptions cost?",
            "How much have we spent on gifts?",
            "How can we cut our car costs?",
        )
    }
}
