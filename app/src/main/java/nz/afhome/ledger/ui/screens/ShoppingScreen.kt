package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.ShoppingSource
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.normalizeName
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.Empty
import nz.afhome.ledger.ui.SectionCard

@Composable
fun ShoppingScreen() {
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    val list by app.repo.dao.shoppingFlow().collectAsState(initial = emptyList())
    var newItem by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // Refresh "usually due" suggestions each time the list is opened.
    LaunchedEffect(Unit) { app.repo.refreshPredictions() }

    val open = list.filter { !it.done }
    val estimate = open.sumOf { (it.estPrice ?: 0.0) * it.qty }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newItem, { newItem = it }, placeholder = { Text("Add to list… (e.g. masoor dal)") },
                    singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val name = newItem.trim()
                    if (name.isNotEmpty()) scope.launch {
                        app.repo.addShopping(name, app.repo.categorizer().categorize(normalizeName(name), null))
                        newItem = ""
                    }
                }) { Icon(Icons.Default.Add, "Add") }
            }
        }
        item {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (open.isEmpty()) "List is empty" else "${open.size} to buy" + if (estimate > 0) " · about ${money(estimate)}" else "",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = {
                    scope.launch {
                        val n = app.repo.refreshPredictions()
                        message = if (n == 0) "Nothing else looks due right now." else "Added $n item${if (n > 1) "s" else ""} you usually buy around now."
                    }
                }) { Icon(Icons.Default.AutoAwesome, null); Text(" Suggest") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (list.isEmpty()) item {
            Empty("Items appear here when something is used up at home, or when it's about time to re-buy based on your purchase history.")
        }
        open.groupBy { Category.of(it.category) }.toSortedMap(compareBy { it.ordinal }).forEach { (cat, rows) ->
            item { Text(cat.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
            items(rows, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable { scope.launch { app.repo.toggleShopping(s) } }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.done, { scope.launch { app.repo.toggleShopping(s) } })
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.Medium)
                        val bits = listOfNotNull(
                            ShoppingSource.of(s.source).takeIf { it != ShoppingSource.MANUAL }?.label,
                            s.estPrice?.let { "~${money(it)}" },
                            s.bestStore?.let { "cheapest at $it" },
                        )
                        if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { app.repo.deleteShopping(s) } }) { Icon(Icons.Default.Close, "Remove") }
                }
            }
        }
        val done = list.filter { it.done }
        if (done.isNotEmpty()) item {
            SectionCard("In the trolley (${done.size})", Modifier.padding(top = 16.dp), action = {
                TextButton(onClick = { scope.launch { app.repo.clearDoneShopping() } }) { Text("Clear") }
            }) {
                done.forEach { s ->
                    Text(s.name, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { app.repo.toggleShopping(s) } }.padding(vertical = 6.dp))
                }
            }
        }
        item {
            Text(
                "Tip: scanning the receipt afterwards ticks things off automatically and restocks the home inventory.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
