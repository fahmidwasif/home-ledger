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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import nz.afhome.ledger.data.HomeRoom
import nz.afhome.ledger.data.InventoryItem
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.UsePurpose
import nz.afhome.ledger.data.fmtShort
import nz.afhome.ledger.data.normalizeName
import nz.afhome.ledger.data.qtyText
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.ChipChoice
import nz.afhome.ledger.ui.DateButton
import nz.afhome.ledger.ui.Dropdown
import nz.afhome.ledger.ui.Empty
import nz.afhome.ledger.ui.NumberField
import nz.afhome.ledger.ui.PersonChoice

@Composable
fun InventoryScreen(onStartingStock: () -> Unit) {
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    val items by app.repo.dao.inventoryFlow().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var room by remember { mutableStateOf<HomeRoom?>(null) }
    var showEmpty by remember { mutableStateOf(false) }
    var using by remember { mutableStateOf<InventoryItem?>(null) }
    var editing by remember { mutableStateOf<InventoryItem?>(null) }

    val filtered = items.filter { i ->
        (showEmpty || i.quantity > 0) && (room == null || i.room == room!!.name) &&
            (query.isBlank() || i.name.contains(query, true) || (i.spot?.contains(query, true) == true))
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
            item {
                androidx.compose.material3.OutlinedButton(onClick = onStartingStock, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Icon(nz.afhome.ledger.ui.Magic.Trunk, null); Text("  Add what's already at home (\$0)")
                }
            }
            item {
                OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Find something at home…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    item { FilterChip(room == null, { room = null }, { Text("All rooms") }) }
                    items(HomeRoom.entries.filter { r -> items.any { it.room == r.name } }) { r ->
                        FilterChip(room == r, { room = if (room == r) null else r }, { Text(r.label) })
                    }
                    item { FilterChip(showEmpty, { showEmpty = !showEmpty }, { Text("Show used up") }) }
                }
            }
            if (filtered.isEmpty()) item { Empty(if (items.isEmpty()) "Like the Room of Requirement, this fills up on its own. Scan grocery receipts and items appear here." else "Nothing matches.") }
            filtered.groupBy { HomeRoom.of(it.room) }.toSortedMap(compareBy { it.ordinal }).forEach { (r, list) ->
                item {
                    Text(r.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { it.id }) { i ->
                    Row(Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(i.name, fontWeight = FontWeight.Medium)
                            val bits = listOfNotNull(
                                "${qtyText(i.quantity)}${i.unit?.let { " $it" } ?: ""} left",
                                i.spot,
                                i.expiry?.let { e -> val d = Insights.daysUntil(e); if (d < 0) "expired" else "exp ${e.fmtShort()}" },
                            )
                            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = if (i.quantity <= i.lowThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = { using = i }, enabled = i.quantity > 0) { Text("Use") }
                    }
                    HorizontalDivider()
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = InventoryItem(name = "", normName = "", category = Category.PANTRY.name, room = HomeRoom.PANTRY.name, quantity = 1.0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, "Add item") }
    }

    using?.let { item ->
        UseDialog(item, app.prefs.defaultPerson.let { Person.of(it) }, onDismiss = { using = null }) { qty, purpose, person ->
            scope.launch { app.repo.useItem(item, qty, purpose, person) }
            using = null
        }
    }
    editing?.let { item ->
        EditItemDialog(item, onDismiss = { editing = null },
            onDelete = if (item.id != 0L) ({ scope.launch { app.repo.deleteInventory(item) }; editing = null }) else null,
        ) { updated -> scope.launch { app.repo.saveInventory(updated) }; editing = null }
    }
}

@Composable
private fun UseDialog(item: InventoryItem, defaultPerson: Person, onDismiss: () -> Unit, onUse: (Double, UsePurpose, Person) -> Unit) {
    var qty by remember { mutableStateOf<Double?>(if (item.quantity >= 1) 1.0 else item.quantity) }
    var purpose by remember { mutableStateOf(if (Category.of(item.category).group == nz.afhome.ledger.data.CategoryGroup.GROCERY) UsePurpose.HOME_MEAL else UsePurpose.GENERAL) }
    var person by remember { mutableStateOf(defaultPerson) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${qtyText(item.quantity)} at home · ${HomeRoom.of(item.room).label}${item.spot?.let { " – $it" } ?: ""}")
                NumberField("How many / how much", qty, Modifier.fillMaxWidth(), item.unit) { qty = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { qty = item.quantity }) { Text("All of it") }
                    TextButton(onClick = { qty = item.quantity / 2 }) { Text("Half") }
                }
                Text("What for?", style = MaterialTheme.typography.labelLarge)
                ChipChoice(UsePurpose.entries, purpose, { it.label }) { purpose = it }
                Text("Who?", style = MaterialTheme.typography.labelLarge)
                PersonChoice(person) { person = it }
            }
        },
        confirmButton = { TextButton(onClick = { qty?.let { onUse(it.coerceAtMost(item.quantity), purpose, person) } }, enabled = (qty ?: 0.0) > 0) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditItemDialog(item: InventoryItem, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (InventoryItem) -> Unit) {
    var i by remember { mutableStateOf(item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.id == 0L) "Add to home stock" else "Edit item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(i.name, { i = i.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Quantity", i.quantity, Modifier.weight(1f)) { i = i.copy(quantity = it ?: 0.0) }
                    OutlinedTextField(i.unit ?: "", { i = i.copy(unit = it.ifBlank { null }) }, label = { Text("Unit") }, singleLine = true,
                        modifier = Modifier.weight(1f), placeholder = { Text("pk, kg, bottle") })
                }
                Dropdown("Room", HomeRoom.of(i.room), HomeRoom.entries, { it.label }, Modifier.fillMaxWidth()) { i = i.copy(room = it.name) }
                OutlinedTextField(i.spot ?: "", { i = i.copy(spot = it.ifBlank { null }) }, label = { Text("Exact spot (optional)") },
                    placeholder = { Text("e.g. top shelf, blue box under sink") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown("Category", Category.of(i.category), Category.entries, { it.label }, Modifier.fillMaxWidth()) { i = i.copy(category = it.name) }
                NumberField("Remind me when down to", i.lowThreshold, Modifier.fillMaxWidth()) { i = i.copy(lowThreshold = it ?: 0.0) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateButton("Best before", i.expiry) { i = i.copy(expiry = it) }
                    if (i.expiry != null) TextButton(onClick = { i = i.copy(expiry = null) }) { Text("Clear") }
                }
                onDelete?.let { TextButton(onClick = it) { Text("Delete item", color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(i.copy(normName = normalizeName(i.name))) }, enabled = i.name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
