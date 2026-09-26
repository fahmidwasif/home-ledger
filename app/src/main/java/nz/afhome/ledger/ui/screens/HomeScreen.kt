package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.afhome.ledger.analysis.Insights
import nz.afhome.ledger.analysis.Period
import nz.afhome.ledger.analysis.range
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.fmtDate
import nz.afhome.ledger.data.fmtShort
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.money0
import nz.afhome.ledger.data.today
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.SectionCard
import nz.afhome.ledger.ui.StatTile

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit, onManual: () -> Unit, onReceipts: () -> Unit, onReceipt: (Long) -> Unit, onCar: () -> Unit, onLunch: () -> Unit,
    onSettings: () -> Unit, onSubscriptions: () -> Unit, onStartingStock: () -> Unit,
) {
    var quick by remember { mutableStateOf<QuickPreset?>(null) }
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    val receipts by app.repo.dao.allReceiptsFlow().collectAsState(initial = emptyList())
    val list by app.repo.dao.receiptsFlow().collectAsState(initial = emptyList())
    val lunches by app.repo.dao.lunchFlow().collectAsState(initial = emptyList())
    val vehicles by app.repo.dao.vehiclesFlow().collectAsState(initial = emptyList())
    val inventory by app.repo.dao.inventoryFlow().collectAsState(initial = emptyList())
    val fuel by app.repo.dao.fuelFlow().collectAsState(initial = emptyList())
    app.prefs.changes.collectAsState().value

    val month = Period.THIS_MONTH.range()
    val last = Period.LAST_MONTH.range()
    val monthTotal = receipts.filter { it.date in month }.sumOf { it.total }
    val lastTotal = receipts.filter { it.date in last }.sumOf { it.total }
    val anika = receipts.filter { it.date in month && it.purchaser == Person.ANIKA.name }.sumOf { it.total }
    val fahmid = receipts.filter { it.date in month && it.purchaser == Person.FAHMID.name }.sumOf { it.total }
    val todayLunch = lunches.filter { it.date == today() }.map { it.person }.toSet()

    val reminders = buildList {
        vehicles.forEach { v ->
            nz.afhome.ledger.analysis.FuelGauge.estimate(v, fuel)?.takeIf { it.low }?.let {
                add("${v.name}: fuel low, about ${it.rangeKm} km left. Filling up ≈ ${money(it.fillCost)}")
            }
            listOf("WoF" to v.wofExpiry, "Rego" to v.regoExpiry, "Insurance" to v.insuranceRenewal).forEach { (label, d) ->
                if (d != null) {
                    val left = Insights.daysUntil(d)
                    if (left <= 30) add("${v.name} $label ${if (left < 0) "expired" else "due"} ${d.fmtDate()}" + if (left >= 0) " ($left days)" else "")
                }
            }
        }
        inventory.filter { it.quantity > 0 && it.expiry != null && Insights.daysUntil(it.expiry) in 0..3 }
            .forEach { add("${it.name} expires ${it.expiry!!.fmtShort()}") }
        val low = inventory.count { it.quantity <= it.lowThreshold }
        if (low > 0) add("$low item${if (low > 1) "s" else ""} used up or running low. They're on the shopping list.")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Anika & Fahmid Home", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Everything stays on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("This month", money0(monthTotal), Modifier.weight(1f),
                    sub = if (lastTotal > 0) "Last month ${money0(lastTotal)}" else null)
                StatTile("Anika · Fahmid", "${money0(anika)} · ${money0(fahmid)}", Modifier.weight(1f), sub = "paid this month")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(nz.afhome.ledger.ui.Magic.Wand, null); Spacer(Modifier.width(8.dp)); Text("Accio receipt!")
                }
                OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(8.dp)); Text("Add manually")
                }
            }
        }
        item {
            SectionCard("Spent without a receipt?") {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickPreset.entries.forEach { p -> AssistChip(onClick = { quick = p }, label = { Text(p.label) }) }
                }
            }
        }
        item {
            SectionCard("Lunch from home today?", icon = nz.afhome.ledger.ui.Magic.Cauldron, action = { AssistChip(onClick = onLunch, label = { Text("History") }, leadingIcon = { Icon(nz.afhome.ledger.ui.Magic.Cauldron, null) }) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Person.ANIKA, Person.FAHMID).forEach { p ->
                        val done = p.name in todayLunch
                        FilledTonalButton(
                            onClick = { if (!done) scope.launch { app.repo.logLunch(p, null) } },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (done) "✓ ${p.label}" else "🍱 ${p.label}") }
                    }
                }
                val monthLunches = lunches.filter { it.date in month }
                if (monthLunches.isNotEmpty()) Text(
                    "${monthLunches.size} packed lunch${if (monthLunches.size == 1) "" else "es"} this month, about ${money0(monthLunches.sumOf { it.boughtCost - it.packedCost })} saved",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (inventory.isEmpty()) item {
            Card(Modifier.clickable(onClick = onStartingStock), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(nz.afhome.ledger.ui.Magic.Trunk, null); Spacer(Modifier.width(12.dp))
                    Text("Start your home stock: tap to add what's already in the pantry (flour, oil, rice…) at no cost.")
                }
            }
        }
        if (reminders.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null); Spacer(Modifier.width(8.dp)); Text("Heads up", fontWeight = FontWeight.SemiBold)
                    }
                    reminders.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (!app.backup.hasTarget || !app.backup.hasPin) item {
            Card(Modifier.clickable(onClick = onSettings), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(nz.afhome.ledger.ui.Magic.VaultKey, null); Spacer(Modifier.width(12.dp))
                    Text("Your Gringotts vault (Google Drive backup) isn't set up yet. Tap to protect your data for when you change phones.")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onCar, label = { Text("Car") }, leadingIcon = { Icon(nz.afhome.ledger.ui.Magic.FlyingCar, null) })
                AssistChip(onClick = onReceipts, label = { Text("All receipts") }, leadingIcon = { Icon(nz.afhome.ledger.ui.Magic.Diary, null) })
                AssistChip(onClick = onSubscriptions, label = { Text("Subscriptions") }, leadingIcon = { Icon(nz.afhome.ledger.ui.Magic.TimeTurner, null) })
            }
        }
        item {
            SectionCard("Recent purchases", icon = nz.afhome.ledger.ui.Magic.Diary) {
                if (list.isEmpty()) Text("Nothing yet. Scan your first receipt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.take(6).forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { onReceipt(r.id) }.padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(r.store, fontWeight = FontWeight.Medium)
                            Text("${r.date.fmtShort()} · ${Person.of(r.purchaser).label} · ${r.itemCount} items",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(r.total), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    quick?.let { p ->
        QuickSpendDialog(p, Person.of(app.prefs.defaultPerson), { app.prefs.lastAccount(it) }, onDismiss = { quick = null }) { what, amount, cat, person, account, day ->
            scope.launch { app.repo.quickSpend(what, amount, cat, person, account, day) }
            quick = null
        }
    }
}
