package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.afhome.ledger.ai.LocalLlm
import nz.afhome.ledger.analysis.Auckland
import nz.afhome.ledger.analysis.Insights
import nz.afhome.ledger.analysis.Period
import nz.afhome.ledger.analysis.range
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.fmtShort
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.money0
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.ColumnBars
import nz.afhome.ledger.ui.Dropdown
import nz.afhome.ledger.ui.HBars
import nz.afhome.ledger.ui.LegendDot
import nz.afhome.ledger.ui.LocalChart
import nz.afhome.ledger.ui.MoneyField
import nz.afhome.ledger.ui.ProgressRow
import nz.afhome.ledger.ui.SectionCard
import nz.afhome.ledger.ui.StatTile
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun InsightsScreen() {
    val app = LocalContext.current.ledger
    val dao = app.repo.dao
    val scope = rememberCoroutineScope()
    val chart = LocalChart.current
    val receipts by dao.allReceiptsFlow().collectAsState(initial = emptyList())
    val items by dao.allItemsFlow().collectAsState(initial = emptyList())
    val lunches by dao.lunchFlow().collectAsState(initial = emptyList())
    val fuel by dao.fuelFlow().collectAsState(initial = emptyList())
    val usage by dao.usageFlow().collectAsState(initial = emptyList())
    val budgets by dao.budgetsFlow().collectAsState(initial = emptyList())
    val vehicles by dao.vehiclesFlow().collectAsState(initial = emptyList())
    val llmState by app.llm.state.collectAsState()
    var period by remember { mutableStateOf(Period.THIS_MONTH) }
    var budgetDialog by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }

    val r = remember(period, receipts, items, lunches, fuel, usage, budgets, vehicles) {
        Insights.build(period.range(earliest = receipts.minOfOrNull { it.date }), receipts, items, lunches, fuel, usage, budgets, vehicles, app.prefs.weeklyGroceryTarget)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(nz.afhome.ledger.ui.Magic.Galleons, null, tint = MaterialTheme.colorScheme.secondary)
                Text("  Gringotts report", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(Period.entries) { p -> FilterChip(period == p, { period = p }, { Text(p.label) }) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val change = if (r.previousTotal > 0) (r.total - r.previousTotal) / r.previousTotal * 100 else null
                StatTile("Spent", money0(r.total), Modifier.weight(1f),
                    sub = change?.let { "${if (it >= 0) "▲" else "▼"} ${abs(it).toInt()}% vs previous" })
                StatTile("Groceries / week", money0(r.weeklyGroceries), Modifier.weight(1f),
                    sub = "Auckland couple: \$${Auckland.COUPLE_GROCERY_LOW.toInt()}–${Auckland.COUPLE_GROCERY_HIGH.toInt()}")
            }
        }
        item {
            SectionCard("What stands out", icon = nz.afhome.ledger.ui.Magic.Diadem) {
                r.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) }
                if (llmState != LocalLlm.State.NO_MODEL) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = {
                        aiBusy = true
                        scope.launch {
                            aiText = runCatching { app.assistant.deepAnalysis() }.getOrElse { "The AI couldn't run: ${it.message}" }
                            aiBusy = false
                        }
                    }, enabled = !aiBusy) {
                        if (aiBusy) nz.afhome.ledger.ui.HallowsLoader(22.dp) else Icon(nz.afhome.ledger.ui.Magic.Owl, null)
                        Text(if (aiBusy) "  The Owl is thinking…" else "  Deep analysis with on-device AI")
                    }
                }
                aiText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        item {
            SectionCard("Last 12 months") {
                ColumnBars(
                    r.monthly.map { it.first.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) }, r.monthly.map { it.second },
                    fullLabels = r.monthly.map { "${it.first.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${it.first.year}" },
                )
            }
        }
        item {
            SectionCard("Where the money went") {
                if (r.byCategory.isEmpty()) Text("No purchases in this period.")
                HBars(r.byCategory.take(10).map { it.first.label to it.second })
            }
        }
        item {
            SectionCard("Anika vs Fahmid") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { r.byPerson.forEach { LegendDot(chart.person.getValue(it.first), it.first.label) } }
                Spacer(Modifier.height(8.dp))
                HBars(r.byPerson.map { it.first.label to it.second }, colorOf = { i -> chart.person.getValue(r.byPerson[i].first) })
                r.personTopCategories.filter { it.value.isNotEmpty() }.forEach { (p, cats) ->
                    Text("${p.label} mostly bought: " + cats.joinToString { "${it.first.label} (${money0(it.second)})" },
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { SectionCard("Paid from", icon = nz.afhome.ledger.ui.Magic.Ring) { HBars(r.byAccount) } }
        item { SectionCard("Shops") { HBars(r.byStore.map { "${it.label} · ${it.extra}" to it.value }) } }
        item {
            SectionCard("Bangladeshi & South Asian groceries") {
                Text("${money0(r.deshiTotal)} · ${(r.deshiShare * 100).toInt()}% of grocery spend", fontWeight = FontWeight.Medium)
                if (r.deshiTopItems.isNotEmpty()) { Spacer(Modifier.height(8.dp)); HBars(r.deshiTopItems.map { it.label to it.value }) }
                if (r.deshiStores.isNotEmpty()) Text("Bought at: " + r.deshiStores.joinToString { "${it.label} ${money0(it.value)}" },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            SectionCard("Lunch: packed vs bought", icon = nz.afhome.ledger.ui.Magic.Cauldron) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Packed from home", "${r.packedLunches}", Modifier.weight(1f), sub = "≈ ${money0(r.lunchSavings)} saved")
                    StatTile("Bought at work", money0(r.boughtLunch), Modifier.weight(1f), sub = "Eating out ${money0(r.eatingOut)}")
                }
                if (r.packedLunchesByPerson.isNotEmpty()) Text(r.packedLunchesByPerson.joinToString { "${it.first.label}: ${it.second}" },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            SectionCard("Gifts & giving", icon = nz.afhome.ledger.ui.Magic.Cup) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Gifts", money0(r.giftTotal), Modifier.weight(1f))
                    StatTile("Zakat, donations & family", money0(r.givingTotal), Modifier.weight(1f))
                }
                if (r.giftsByRecipient.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("By person", style = MaterialTheme.typography.labelLarge); HBars(r.giftsByRecipient.take(6).map { it.label to it.value }) }
                if (r.giftsByOccasion.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("By occasion", style = MaterialTheme.typography.labelLarge); HBars(r.giftsByOccasion.map { it.label to it.value }) }
            }
        }
        item {
            SectionCard("Car", icon = nz.afhome.ledger.ui.Magic.FlyingCar) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Car total", money0(r.carTotal), Modifier.weight(1f), sub = "fuel ${money0(r.fuelTotal)}")
                    StatTile("Yearly estimate", money0(r.carYearProjection), Modifier.weight(1f), sub = "fuel + rego + WoF + insurance")
                }
                val bits = listOfNotNull(
                    r.fuelLitres.takeIf { it > 0 }?.let { "${it.toInt()} L" },
                    r.avgPricePerL?.let { "avg ${money(it)}/L" },
                    r.litresPer100km?.let { String.format(Locale.US, "%.1f L/100km", it) },
                )
                if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            SectionCard("Which days we spend") {
                ColumnBars(r.weekday.map { it.first.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }, r.weekday.map { it.second }, highlightLast = false)
                Text("${r.topUpCount} small top-up grocery trips (< \$30) · ${money0(r.topUpTotal)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (r.priceRises.isNotEmpty()) item {
            SectionCard("Price watch") {
                r.priceRises.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(c.name, Modifier.weight(1f), maxLines = 1)
                        Text("${money(c.first)} → ${money(c.last)}  ${if (c.pct > 0) "▲" else "▼"}${abs(c.pct).toInt()}%",
                            color = if (c.pct > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
                Text("Since ${r.priceRises.minOf { it.firstDay }.fmtShort()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionCard("Top items") { HBars(r.topItems.map { "${it.label} ${it.extra}" to it.value }) } }
        if (r.wastedValue > 0) item {
            SectionCard("Thrown out") {
                Text("${money0(r.wastedValue)} of food expired or was thrown out", fontWeight = FontWeight.Medium)
                HBars(r.wastedItems.map { it.label to it.value })
            }
        }
        item {
            SectionCard("Budgets", icon = nz.afhome.ledger.ui.Magic.Locket, action = { TextButton(onClick = { budgetDialog = true }) { Text("Set") } }) {
                if (r.budgets.isEmpty()) Text("No budgets yet. Set monthly limits for categories like Eating Out or Groceries.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.budgets.forEach { (c, spent, limit) -> ProgressRow(c.label, spent, limit); Spacer(Modifier.height(8.dp)) }
            }
        }
        item {
            Text("Auckland reference (Sept 2026): petrol car rego ${money(Auckland.REGO_PETROL_YEAR)}/yr · WoF ≈ \$76–91 · diesel/EV RUC \$76 per 1,000 km · no Auckland regional fuel tax since July 2024.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (budgetDialog) BudgetDialog(budgets.associate { Category.of(it.category) to it.monthly }, onDismiss = { budgetDialog = false }) { c, v ->
        scope.launch { app.repo.setBudget(c, v) }
    }
}

@Composable
private fun BudgetDialog(current: Map<Category, Double>, onDismiss: () -> Unit, onSet: (Category, Double?) -> Unit) {
    var cat by remember { mutableStateOf(Category.DINING) }
    var amount by remember(cat) { mutableStateOf(current[cat]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Dropdown("Category", cat, Category.entries, { it.label }, Modifier.fillMaxWidth()) { cat = it }
                MoneyField("Limit per month (blank = none)", amount, Modifier.fillMaxWidth()) { amount = it }
                current.forEach { (c, v) -> Text("${c.label}: ${money0(v)}/month", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(cat, amount); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
