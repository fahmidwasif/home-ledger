package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
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
import nz.afhome.ledger.analysis.Insights
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.CategoryGroup
import nz.afhome.ledger.data.Frequency
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.Subscription
import nz.afhome.ledger.data.fmtDate
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.money0
import nz.afhome.ledger.data.today
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.AccountChoice
import nz.afhome.ledger.ui.ChipChoice
import nz.afhome.ledger.ui.DateButton
import nz.afhome.ledger.ui.Dropdown
import nz.afhome.ledger.ui.Magic
import nz.afhome.ledger.ui.MoneyField
import nz.afhome.ledger.ui.PersonChoice
import nz.afhome.ledger.ui.SectionCard
import nz.afhome.ledger.ui.StatTile

/** Common NZ recurring payments to start from; prices are left for the user to fill in. */
private val PRESETS = listOf(
    "Netflix" to Category.SUBSCRIPTIONS, "Spotify" to Category.SUBSCRIPTIONS, "Disney+" to Category.SUBSCRIPTIONS,
    "Neon" to Category.SUBSCRIPTIONS, "YouTube Premium" to Category.SUBSCRIPTIONS, "iCloud / Google One" to Category.SUBSCRIPTIONS,
    "Mobile plan" to Category.INTERNET_PHONE, "Broadband" to Category.INTERNET_PHONE, "Power" to Category.UTILITIES,
    "Gym" to Category.FITNESS, "Health insurance" to Category.INSURANCE, "Contents insurance" to Category.INSURANCE,
    "Car insurance" to Category.CAR_ADMIN, "Rent" to Category.RENT, "AT HOP auto top-up" to Category.TRANSPORT,
    "Family support" to Category.FAMILY_SUPPORT,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SubscriptionsScreen() {
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    val subs by app.repo.dao.subscriptionsFlow().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Subscription?>(null) }
    val active = subs.filter { it.active }
    val monthly = active.sumOf { it.amount * Frequency.of(it.frequency).perYear / 12 }

    fun blank(name: String = "", category: Category = Category.SUBSCRIPTIONS) = Subscription(
        name = name, amount = 0.0, category = category.name, nextDue = today(),
        purchaser = app.prefs.defaultPerson, payment = app.prefs.lastAccount(Person.of(app.prefs.defaultPerson))?.name,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Per month", money0(monthly), Modifier.weight(1f), sub = "${active.size} active")
                    StatTile("Per year", money0(monthly * 12), Modifier.weight(1f))
                }
            }
            item {
                Text(
                    "Payments are recorded as spending automatically on each due date, and you get a reminder a few days before, " +
                        "so you have time to cancel anything you don't use.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (subs.isEmpty()) item {
                SectionCard("Quick add", icon = Magic.TimeTurner) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS.forEach { (n, c) -> SuggestionChip(onClick = { editing = blank(n, c) }, label = { Text(n) }) }
                    }
                }
            }
            active.groupBy { Category.of(it.category).group }.toSortedMap(compareBy { it.ordinal }).forEach { (g, list) ->
                item { Text(g.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(list, key = { it.id }) { s -> SubRow(s) { editing = s } }
            }
            val paused = subs.filter { !it.active }
            if (paused.isNotEmpty()) {
                item { Text("Paused / cancelled", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(paused, key = { it.id }) { s -> SubRow(s) { editing = s } }
            }
        }
        FloatingActionButton(onClick = { editing = blank() }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Add recurring payment")
        }
    }

    editing?.let { s ->
        SubscriptionDialog(
            s, onDismiss = { editing = null },
            onDelete = if (s.id != 0L) ({ scope.launch { app.repo.deleteSubscription(s) }; editing = null }) else null,
        ) { updated ->
            scope.launch { app.repo.saveSubscription(updated); app.repo.processSubscriptions() }
            editing = null
        }
    }
}

@Composable
private fun SubRow(s: Subscription, onClick: () -> Unit) {
    val left = Insights.daysUntil(s.nextDue)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.name, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(
                    Frequency.of(s.frequency).label,
                    if (s.active) "next ${s.nextDue.fmtDate()}" + if (left in 0..7) " ($left days)" else "" else "paused",
                    PayAccount.of(s.payment)?.label,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (s.active && left in 0..3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(money(s.amount), fontWeight = FontWeight.Medium)
    }
    HorizontalDivider()
}

@Composable
private fun SubscriptionDialog(s: Subscription, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Subscription) -> Unit) {
    var x by remember { mutableStateOf(s) }
    val billCats = Category.entries.filter {
        it.group in setOf(CategoryGroup.SUBSCRIPTIONS, CategoryGroup.BILLS, CategoryGroup.WELLBEING, CategoryGroup.TRANSPORT, CategoryGroup.GIVING) ||
            it == Category.CAR_ADMIN || it == Category.EDUCATION || it == Category.OTHER
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.id == 0L) "New recurring payment" else "Edit ${s.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(x.name, { x = x.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField("Amount each time", x.amount.takeIf { it > 0 }, Modifier.fillMaxWidth()) { x = x.copy(amount = it ?: 0.0) }
                Text("How often", style = MaterialTheme.typography.labelLarge)
                ChipChoice(Frequency.entries, Frequency.of(x.frequency), { it.label }) { x = x.copy(frequency = it.name) }
                DateButton("Next payment", x.nextDue) { x = x.copy(nextDue = it) }
                Dropdown("Category", Category.of(x.category), billCats, { it.label }, Modifier.fillMaxWidth()) { x = x.copy(category = it.name) }
                Text("Whose", style = MaterialTheme.typography.labelLarge)
                PersonChoice(Person.of(x.purchaser)) { x = x.copy(purchaser = it.name) }
                Text("Paid from", style = MaterialTheme.typography.labelLarge)
                AccountChoice(PayAccount.of(x.payment)) { x = x.copy(payment = it.name) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Record automatically when due", Modifier.weight(1f)); Switch(x.autoLog, { x = x.copy(autoLog = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me 3 days before", Modifier.weight(1f)); Switch(x.remindDays > 0, { x = x.copy(remindDays = if (it) 3 else 0) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", Modifier.weight(1f)); Switch(x.active, { x = x.copy(active = it) })
                }
                if (x.nextDue < today()) Text(
                    "The first date is in the past. Payments since then will be recorded when you save.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                )
                onDelete?.let { TextButton(onClick = it) { Text("Delete (keeps past payments)", color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(x.copy(name = x.name.trim())) }, enabled = x.name.isNotBlank() && x.amount > 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
