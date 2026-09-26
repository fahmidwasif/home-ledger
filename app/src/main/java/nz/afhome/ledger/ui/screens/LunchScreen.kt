package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import nz.afhome.ledger.analysis.Period
import nz.afhome.ledger.analysis.range
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.fmtShort
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.money0
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.PersonChoice
import nz.afhome.ledger.ui.SectionCard
import nz.afhome.ledger.ui.StatTile

/** Packed lunches for work: logging, history and savings. */
@Composable
fun LunchScreen() {
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    val lunches by app.repo.dao.lunchFlow().collectAsState(initial = emptyList())
    var person by remember { mutableStateOf(Person.of(app.prefs.defaultPerson)) }
    var meal by remember { mutableStateOf("") }
    val month = Period.THIS_MONTH.range()
    val year = Period.THIS_YEAR.range()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val m = lunches.filter { it.date in month }
                val y = lunches.filter { it.date in year }
                StatTile("This month", "${m.size} lunches", Modifier.weight(1f), sub = "≈ ${money0(m.sumOf { it.boughtCost - it.packedCost })} saved")
                StatTile("This year", "${y.size} lunches", Modifier.weight(1f), sub = "≈ ${money0(y.sumOf { it.boughtCost - it.packedCost })} saved")
            }
        }
        item {
            SectionCard("Log a packed lunch", icon = nz.afhome.ledger.ui.Magic.Cauldron) {
                PersonChoice(person) { person = it }
                OutlinedTextField(meal, { meal = it }, label = { Text("What was it? (optional)") }, placeholder = { Text("e.g. khichuri & egg curry") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { scope.launch { app.repo.logLunch(person, meal.ifBlank { null }); meal = "" } }) { Text("Log for today") }
                Text(
                    "Savings use ${money(app.prefs.lunchBoughtCost)} for a bought lunch vs ${money(app.prefs.lunchPackedCost)} for one from home (change in Settings). " +
                        "Using food from Stock with \"Packed lunch for work\" logs it too.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(lunches, key = { it.id }) { l ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${l.date.fmtShort()} · ${Person.of(l.person).label}", fontWeight = FontWeight.Medium)
                    l.meal?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Text("+${money(l.boughtCost - l.packedCost)}", color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { scope.launch { app.repo.deleteLunch(l) } }) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }
}
