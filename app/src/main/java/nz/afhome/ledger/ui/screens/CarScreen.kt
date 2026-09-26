package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import nz.afhome.ledger.analysis.Auckland
import nz.afhome.ledger.analysis.Insights
import nz.afhome.ledger.data.FuelLog
import nz.afhome.ledger.data.Vehicle
import nz.afhome.ledger.data.fmtDate
import nz.afhome.ledger.data.fmtShort
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.today
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.DateButton
import nz.afhome.ledger.ui.Dropdown
import nz.afhome.ledger.ui.MoneyField
import nz.afhome.ledger.ui.NumberField
import nz.afhome.ledger.ui.SectionCard

@Composable
fun CarScreen() {
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    val vehicles by app.repo.dao.vehiclesFlow().collectAsState(initial = emptyList())
    val fuel by app.repo.dao.fuelFlow().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Vehicle?>(null) }
    var addFuel by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (vehicles.isEmpty()) item {
            SectionCard("Add your car") {
                Text("Add the car to get WoF, rego and insurance reminders and a yearly running-cost estimate.")
                Button(onClick = { editing = Vehicle(name = "Our car") }, modifier = Modifier.padding(top = 8.dp)) { Text("Add car") }
            }
        }
        items(vehicles, key = { it.id }) { v ->
            SectionCard(v.name + (v.plate?.let { " · $it" } ?: ""), icon = nz.afhome.ledger.ui.Magic.FlyingCar, action = { TextButton(onClick = { editing = v }) { Text("Edit") } }) {
                DueRow("WoF", v.wofExpiry)
                DueRow("Registration (rego)", v.regoExpiry)
                DueRow("Insurance renewal", v.insuranceRenewal)
                v.odometer?.let { Text("Odometer: $it km" + (v.nextServiceKm?.let { s -> " · next service at $s km" } ?: "")) }
                Text(
                    "Auckland guide: petrol car rego ${money(Auckland.REGO_PETROL_YEAR)}/yr (from 1 July 2026). WoF ≈ \$76–91 at AA/VTNZ. " +
                        if (v.fuelType.contains("Diesel", true) || v.fuelType.contains("EV", true)) "Diesel/EV also pay RUC: \$76 per 1,000 km." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fuel log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { addFuel = true }) { Text("Add fill-up") }
            }
            Text("Fuel receipts you scan are logged here automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(fuel, key = { it.id }) { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${f.date.fmtShort()} · ${f.station ?: "Fuel"}", fontWeight = FontWeight.Medium)
                    Text("${String.format(java.util.Locale.US, "%.1f", f.litres)} L @ ${money(f.pricePerL)}/L" + (f.odometer?.let { " · $it km" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(money(f.total))
                IconButton(onClick = { scope.launch { app.repo.deleteFuel(f) } }) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }

    editing?.let { v ->
        VehicleDialog(v, onDismiss = { editing = null }) { scope.launch { app.repo.saveVehicle(it) }; editing = null }
    }
    if (addFuel) FuelDialog(vehicles.firstOrNull()?.id, onDismiss = { addFuel = false }) { scope.launch { app.repo.addFuel(it) }; addFuel = false }
}

@Composable
private fun DueRow(label: String, day: Long?) {
    val left = day?.let { Insights.daysUntil(it) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f))
        Text(
            when {
                day == null -> "not set"
                left!! < 0 -> "⚠ expired ${day.fmtDate()}"
                left <= 30 -> "⚠ ${day.fmtDate()} ($left days)"
                else -> day.fmtDate()
            },
            color = if (left != null && left <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VehicleDialog(v: Vehicle, onDismiss: () -> Unit, onSave: (Vehicle) -> Unit) {
    var x by remember { mutableStateOf(v) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Car details") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(x.name, { x = x.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(x.plate ?: "", { x = x.copy(plate = it.uppercase().ifBlank { null }) }, label = { Text("Plate") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown("Fuel", x.fuelType, listOf("Petrol 91", "Petrol 95", "Petrol 98", "Diesel", "Hybrid (petrol)", "EV"), { it }, Modifier.fillMaxWidth()) { x = x.copy(fuelType = it) }
                DateButton("WoF expires", x.wofExpiry) { x = x.copy(wofExpiry = it) }
                DateButton("Rego expires", x.regoExpiry) { x = x.copy(regoExpiry = it) }
                DateButton("Insurance renews", x.insuranceRenewal) { x = x.copy(insuranceRenewal = it) }
                MoneyField("Insurance per year", x.insuranceAnnual, Modifier.fillMaxWidth()) { x = x.copy(insuranceAnnual = it) }
                NumberField("Odometer", x.odometer?.toDouble(), Modifier.fillMaxWidth(), "km") { x = x.copy(odometer = it?.toInt()) }
                NumberField("Next service at", x.nextServiceKm?.toDouble(), Modifier.fillMaxWidth(), "km") { x = x.copy(nextServiceKm = it?.toInt()) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(x) }, enabled = x.name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FuelDialog(vehicleId: Long?, onDismiss: () -> Unit, onSave: (FuelLog) -> Unit) {
    var litres by remember { mutableStateOf<Double?>(null) }
    var total by remember { mutableStateOf<Double?>(null) }
    var odo by remember { mutableStateOf<Double?>(null) }
    var station by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(today()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add fill-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(station, { station = it }, label = { Text("Station") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField("Litres", litres, Modifier.fillMaxWidth(), "L") { litres = it }
                MoneyField("Total", total, Modifier.fillMaxWidth()) { total = it }
                NumberField("Odometer (optional)", odo, Modifier.fillMaxWidth(), "km") { odo = it }
                DateButton("Date", day) { day = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val l = litres!!; val t = total!!
                onSave(FuelLog(vehicleId = vehicleId, date = day, litres = l, pricePerL = t / l, total = t, odometer = odo?.toInt(), station = station.ifBlank { null }))
            }, enabled = (litres ?: 0.0) > 0 && (total ?: 0.0) > 0) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
