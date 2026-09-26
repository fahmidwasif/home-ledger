package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.HomeRoom
import nz.afhome.ledger.data.InventoryItem
import nz.afhome.ledger.data.normalizeName
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.Dropdown
import nz.afhome.ledger.ui.NumberField
import nz.afhome.ledger.ui.SectionCard

/** Staples a Bangladeshi household in Auckland is likely to already have, grouped for quick picking. */
private val STAPLES: List<Pair<String, List<Pair<String, Category>>>> = listOf(
    "Rice, dal & flour" to listOf(
        "Chinigura rice" to Category.DESHI, "Basmati rice" to Category.DESHI, "Masoor dal" to Category.DESHI, "Moong dal" to Category.DESHI,
        "Chana dal" to Category.DESHI, "Atta" to Category.DESHI, "Plain flour" to Category.PANTRY, "Besan" to Category.DESHI,
        "Suji" to Category.DESHI, "Chira (poha)" to Category.DESHI, "Muri" to Category.DESHI,
    ),
    "Oil, spices & sauces" to listOf(
        "Cooking oil" to Category.PANTRY, "Mustard oil" to Category.DESHI, "Ghee" to Category.DESHI, "Salt" to Category.PANTRY,
        "Sugar" to Category.PANTRY, "Turmeric" to Category.DESHI, "Chilli powder" to Category.DESHI, "Cumin" to Category.DESHI,
        "Coriander powder" to Category.DESHI, "Garam masala" to Category.DESHI, "Panch phoron" to Category.DESHI,
        "Kalonji" to Category.DESHI, "Bay leaves" to Category.DESHI, "Cardamom" to Category.DESHI, "Cinnamon" to Category.DESHI,
        "Biryani masala" to Category.DESHI, "Tomato sauce" to Category.PANTRY, "Soy sauce" to Category.PANTRY,
    ),
    "Kitchen & fridge" to listOf(
        "Onions" to Category.PRODUCE, "Garlic" to Category.PRODUCE, "Ginger" to Category.PRODUCE, "Potatoes" to Category.PRODUCE,
        "Green chillies" to Category.PRODUCE, "Eggs" to Category.DAIRY, "Milk" to Category.DAIRY, "Butter" to Category.DAIRY,
        "Yoghurt" to Category.DAIRY, "Chicken (frozen)" to Category.MEAT, "Fish (frozen)" to Category.MEAT, "Frozen paratha" to Category.FROZEN,
        "Bread" to Category.BAKERY, "Tea" to Category.PANTRY, "Coffee" to Category.PANTRY, "Biscuits" to Category.SNACKS,
        "Chanachur" to Category.DESHI, "Noodles" to Category.PANTRY,
    ),
    "Bathroom & laundry" to listOf(
        "Toilet paper" to Category.HOUSEHOLD, "Dishwashing liquid" to Category.HOUSEHOLD, "Laundry powder" to Category.HOUSEHOLD,
        "Rubbish bags" to Category.HOUSEHOLD, "Paper towels" to Category.HOUSEHOLD, "Cling wrap" to Category.HOUSEHOLD,
        "Shampoo" to Category.PERSONAL, "Soap / body wash" to Category.PERSONAL, "Toothpaste" to Category.PERSONAL,
        "Panadol" to Category.HEALTH, "Plasters" to Category.HEALTH,
    ),
)

private data class Pick(val name: String, val category: Category, val qty: Double = 1.0, val room: HomeRoom = category.defaultRoom, val unit: String? = null)

/** Record what's already at home, at no cost, so stock and shopping lists are right from day one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartingStockScreen(onDone: () -> Unit) {
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    val picks = remember { mutableStateListOf<Pick>() }
    var custom by remember { mutableStateOf("") }

    fun toggle(name: String, category: Category) {
        val i = picks.indexOfFirst { it.name == name }
        if (i >= 0) picks.removeAt(i) else picks += Pick(name, category)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Tap what you already have at home. These are added at \$0: they're stock, not spending. " +
                    "From now on, scanning receipts keeps it up to date.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(STAPLES) { (group, list) ->
            SectionCard(group) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { (n, c) -> FilterChip(picks.any { it.name == n }, { toggle(n, c) }, { Text(n) }) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(custom, { custom = it }, label = { Text("Something else") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val n = custom.trim()
                    if (n.isNotEmpty() && picks.none { it.name.equals(n, true) }) {
                        scope.launch { picks += Pick(n, app.repo.categorizer().categorize(normalizeName(n), null).let { if (it == Category.OTHER) Category.PANTRY else it }) }
                    }
                    custom = ""
                }) { Icon(Icons.Default.Add, "Add") }
            }
        }
        if (picks.isNotEmpty()) {
            item { Text("How much, and where? (${picks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(picks.toList(), key = { it.name }) { p ->
                val idx = picks.indexOfFirst { it.name == p.name }
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { picks.removeAt(idx) }) { Icon(Icons.Default.Close, "Remove") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberField("Qty", p.qty, Modifier.weight(0.6f)) { q -> picks[idx] = picks[idx].copy(qty = q ?: 1.0) }
                        OutlinedTextField(p.unit ?: "", { u -> picks[idx] = picks[idx].copy(unit = u.ifBlank { null }) }, label = { Text("Unit") },
                            placeholder = { Text("bag, kg") }, singleLine = true, modifier = Modifier.weight(0.6f))
                        Dropdown("Room", p.room, HomeRoom.entries, { it.label }, Modifier.weight(1f)) { r -> picks[idx] = picks[idx].copy(room = r) }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        app.repo.addStartingStock(picks.map {
                            InventoryItem(name = it.name, normName = normalizeName(it.name), category = it.category.name,
                                room = it.room.name, quantity = it.qty, unit = it.unit, lowThreshold = 0.0)
                        })
                        onDone()
                    }
                },
                enabled = picks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (picks.isEmpty()) "Pick items above" else "Add ${picks.size} item${if (picks.size > 1) "s" else ""} to home stock") }
        }
    }
}
