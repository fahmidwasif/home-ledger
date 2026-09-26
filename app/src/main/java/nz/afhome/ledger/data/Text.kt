package nz.afhome.ledger.data

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SIZE = Regex("""\b\d+(\.\d+)?\s*(kg|g|gm|ml|l|ltr|pk|pack|ea|each|x|s)\b""")
private val NON_WORD = Regex("""[^a-z ]""")
private val SPACES = Regex("""\s+""")

/** Canonical key for "the same product" across receipts: lower-case words with sizes and numbers removed. */
fun normalizeName(name: String): String =
    name.lowercase()
        .replace(SIZE, " ")
        .replace(NON_WORD, " ")
        .replace(SPACES, " ")
        .trim()
        .ifEmpty { name.lowercase().trim() }

private val nzd = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"))

fun money(v: Double): String = nzd.format(v)
fun money0(v: Double): String = "$" + String.format(Locale.US, "%,.0f", v)

fun today(): Long = LocalDate.now().toEpochDay()
fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)
private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private val shortFmt = DateTimeFormatter.ofPattern("d MMM")
fun Long.fmtDate(): String = toDate().format(dayFmt)
fun Long.fmtShort(): String = toDate().format(shortFmt)

fun qtyText(q: Double): String = if (q == q.toLong().toDouble()) q.toLong().toString() else String.format(Locale.US, "%.2f", q).trimEnd('0').trimEnd('.')
