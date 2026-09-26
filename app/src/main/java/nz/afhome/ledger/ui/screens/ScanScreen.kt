package nz.afhome.ledger.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nz.afhome.ledger.ai.LocalLlm
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.GIFT_OCCASIONS
import nz.afhome.ledger.data.HomeRoom
import nz.afhome.ledger.data.money
import nz.afhome.ledger.ledger
import nz.afhome.ledger.scan.Categorizer
import nz.afhome.ledger.scan.Clarification
import nz.afhome.ledger.scan.Clarifier
import nz.afhome.ledger.scan.DraftItem
import nz.afhome.ledger.scan.ReceiptDraft
import nz.afhome.ledger.scan.ReceiptParser
import nz.afhome.ledger.ui.AccountChoice
import nz.afhome.ledger.ui.DateButton
import nz.afhome.ledger.ui.Dropdown
import nz.afhome.ledger.ui.MoneyField
import nz.afhome.ledger.ui.NumberField
import nz.afhome.ledger.ui.PersonChoice
import nz.afhome.ledger.ui.ScanViewModel
import nz.afhome.ledger.ui.SectionCard

@Composable
fun ScanScreen(vm: ScanViewModel, onSaved: () -> Unit) {
    val draft by vm.draft.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.onCameraResult(ok)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::onGalleryPick) }
    val takePhoto = { camera.launch(vm.newPhotoUri()) }
    val pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(Modifier.fillMaxSize()) {
        busy?.let {
            nz.afhome.ledger.ui.MagicLoading(it, Modifier.fillMaxWidth().padding(16.dp))
        }
        error?.let {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f)); TextButton(onClick = vm::clearError) { Text("OK") }
                }
            }
        }
        val d = draft
        if (d == null) {
            if (busy == null) ScanStart(takePhoto, pickPhoto)
        } else {
            Review(d, vm, onAddPhoto = takePhoto, onSaved = onSaved)
        }
    }
}

@Composable
private fun ScanStart(takePhoto: () -> Unit, pickPhoto: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Scan a receipt", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Lay the receipt flat in good light. Everything is read on this phone. The photo is deleted straight after " +
                "reading, and only the details are kept. For long receipts you can add a second photo.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = takePhoto, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(nz.afhome.ledger.ui.Magic.Wand, null); Spacer(Modifier.width(8.dp)); Text("Accio receipt! (take photo)")
        }
        OutlinedButton(onClick = pickPhoto, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Use a photo from the gallery")
        }
        Text("Gallery photos are read the same way, but the original stays in your gallery. Delete it there if you like.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Review(d: ReceiptDraft, vm: ScanViewModel, onAddPhoto: () -> Unit, onSaved: () -> Unit) {
    val app = LocalContext.current.ledger
    val questions = Clarifier.questions(d)
    val blocking = questions.filter { it.blocking }
    val vehicles by app.repo.dao.vehiclesFlow().collectAsState(initial = emptyList())
    val llmState by app.llm.state.collectAsState()
    val isFuel = Categorizer.isFuelStation(d.store) || d.fuelLitres != null
    var vehicleId by remember(vehicles) { mutableStateOf(vehicles.firstOrNull()?.id) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (questions.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, null); Spacer(Modifier.width(8.dp))
                        Text("A few questions before saving", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    questions.forEach { q -> QuestionRow(q, d, vm) }
                }
            }
        }

        item {
            SectionCard(if (d.manual) "New purchase" else "Receipt details") {
                OutlinedTextField(d.store, { v -> vm.update { it.copy(store = v) } }, label = { Text("Shop") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateButton("Date", d.date) { day -> vm.update { it.copy(date = day) } }
                }
                MoneyField("Total paid", d.total, Modifier.fillMaxWidth()) { v -> vm.update { it.copy(total = v) } }
                Spacer(Modifier.height(8.dp))
                Text("Who bought it?", style = MaterialTheme.typography.labelLarge)
                PersonChoice(d.purchaser) { vm.setPurchaser(it) }
                Text("Paid from", style = MaterialTheme.typography.labelLarge)
                AccountChoice(d.account) { a -> vm.update { it.copy(account = a) } }
                OutlinedTextField(d.notes, { v -> vm.update { it.copy(notes = v) } }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                if (!d.manual) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAddPhoto) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Add another photo") }
                    if (llmState != LocalLlm.State.NO_MODEL) TextButton(onClick = vm::refineWithAi) {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(4.dp)); Text("Re-read with AI")
                    }
                }
            }
        }

        if (isFuel) item {
            SectionCard("Fuel") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Litres", d.fuelLitres, Modifier.weight(1f), "L") { v -> vm.update { it.copy(fuelLitres = v) } }
                    NumberField("Price per litre", d.fuelPricePerL, Modifier.weight(1f), "$/L") { v -> vm.update { it.copy(fuelPricePerL = v) } }
                }
                NumberField("Odometer (optional)", d.odometer?.toDouble(), Modifier.fillMaxWidth(), "km") { v -> vm.update { it.copy(odometer = v?.toInt()) } }
                if (vehicles.size > 1) Dropdown("Car", vehicles.firstOrNull { it.id == vehicleId } ?: vehicles.first(), vehicles, { it.name }) { vehicleId = it.id }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Items (${d.items.size}) · ${money(d.itemsSum)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    vm.update { it.copy(items = it.items + DraftItem(name = "", unitPrice = 0.0, total = 0.0)) }
                }) { Icon(Icons.Default.Add, null); Text("Add item") }
            }
        }
        items(d.items, key = { it.key }) { item -> ItemEditor(item, vm) }

        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.save(vehicleId, onSaved) },
                enabled = blocking.isEmpty() && (d.items.isNotEmpty() || (d.total ?: 0.0) > 0),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (blocking.isEmpty()) "Save purchase" else "Answer ${blocking.size} question${if (blocking.size > 1) "s" else ""} to save") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun QuestionRow(q: Clarification, d: ReceiptDraft, vm: ScanViewModel) {
    val app = LocalContext.current.ledger
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(q.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        when (q.kind) {
            Clarification.Kind.PURCHASER -> PersonChoice(d.purchaser) { vm.setPurchaser(it) }
            Clarification.Kind.ACCOUNT -> AccountChoice(d.account) { a -> vm.update { it.copy(account = a) } }
            Clarification.Kind.STORE -> {
                var t by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("e.g. PAK'nSAVE Royal Oak") })
                    TextButton(onClick = { vm.update { it.copy(store = t.trim()) } }, enabled = t.isNotBlank()) { Text("Set") }
                }
            }
            Clarification.Kind.DATE -> Row {
                DateButton("Date", d.date) { day -> vm.update { it.copy(date = day) } }
                TextButton(onClick = { vm.update { it.copy(date = nz.afhome.ledger.data.today()) } }) { Text("Today") }
            }
            Clarification.Kind.TOTAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                var v by remember { mutableStateOf<Double?>(null) }
                MoneyField("Total", v, Modifier.weight(1f)) { v = it }
                TextButton(onClick = { vm.update { it.copy(total = v) } }, enabled = v != null) { Text("Set") }
                if (d.items.isNotEmpty()) TextButton(onClick = { vm.update { it.copy(total = ReceiptParser.round2(it.itemsSum)) } }) { Text("Use ${money(d.itemsSum)}") }
            }
            Clarification.Kind.MISMATCH -> Column {
                val diff = ReceiptParser.round2((d.total ?: 0.0) - d.itemsSum)
                if (diff > 0) TextButton(onClick = {
                    vm.update { it.copy(items = it.items + DraftItem(name = "Unreadable item(s)", unitPrice = diff, total = diff, category = Category.OTHER, toInventory = false), acknowledged = it.acknowledged + "mismatch") }
                }) { Text("Add ${money(diff)} as an unreadable line") }
                TextButton(onClick = { vm.update { it.copy(total = ReceiptParser.round2(it.itemsSum)) } }) { Text("The items are right. Total is ${money(d.itemsSum)}") }
                TextButton(onClick = { vm.update { it.copy(acknowledged = it.acknowledged + "mismatch") } }) { Text("Keep as is (discounts/fees not listed)") }
            }
            Clarification.Kind.ITEM_NAME -> TextButton(onClick = { vm.update { it.copy(acknowledged = it.acknowledged + q.id) } }) {
                Text("Looks right (or fix it in the list below)")
            }
            Clarification.Kind.NO_ITEMS -> TextButton(
                onClick = { vm.update { it.copy(items = listOf(ReceiptParser.singleLine(it.store, it.total ?: 0.0, Categorizer()))) } },
                enabled = d.total != null,
            ) { Text("Save as one amount") }
            Clarification.Kind.FUEL -> Row(verticalAlignment = Alignment.CenterVertically) {
                var l by remember { mutableStateOf<Double?>(null) }
                NumberField("Litres", l, Modifier.weight(1f), "L") { l = it }
                TextButton(onClick = { vm.update { it.copy(fuelLitres = l) } }, enabled = l != null) { Text("Set") }
                TextButton(onClick = { vm.update { it.copy(acknowledged = it.acknowledged + "fuel") } }) { Text("Not fuel") }
            }
            else -> {}
        }
    }
}

@Composable
private fun ItemEditor(item: DraftItem, vm: ScanViewModel) {
    fun set(f: (DraftItem) -> DraftItem) = vm.update { d -> d.copy(items = d.items.map { if (it.key == item.key) f(it) else it }) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.doubtful) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(item.name, { v -> set { it.copy(name = v) } }, label = { Text("Item") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.update { d -> d.copy(items = d.items.filter { it.key != item.key }) } }) { Icon(Icons.Default.Delete, "Remove") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Qty", item.qty, Modifier.weight(0.7f), item.unit) { q -> set { it.copy(qty = q ?: 1.0, unitPrice = if ((q ?: 0.0) > 0) it.total / q!! else it.unitPrice) } }
                MoneyField("Line total", item.total, Modifier.weight(1f)) { v -> set { it.copy(total = v ?: 0.0, unitPrice = (v ?: 0.0) / it.qty.coerceAtLeast(0.001)) } }
            }
            Dropdown("Category", item.category, Category.entries, { it.label }, Modifier.fillMaxWidth()) { c ->
                set { it.copy(category = c, toInventory = c.stocked, room = c.defaultRoom, isGift = it.isGift || c == Category.GIFTS) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add to home stock", Modifier.weight(1f))
                Switch(item.toInventory && !item.isGift, { v -> set { it.copy(toInventory = v) } }, enabled = !item.isGift)
            }
            if (item.toInventory && !item.isGift) {
                Dropdown("Kept in", item.room, HomeRoom.entries, { it.label }, Modifier.fillMaxWidth()) { r -> set { it.copy(room = r) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(nz.afhome.ledger.ui.Magic.Cup, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(6.dp))
                Text("This is a gift", Modifier.weight(1f))
                Switch(item.isGift, { v -> set { it.copy(isGift = v) } })
            }
            if (item.isGift) {
                OutlinedTextField(item.giftFor, { v -> set { it.copy(giftFor = v) } }, label = { Text("Gift for (name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown("Occasion", item.giftOccasion.ifEmpty { "Choose…" }, GIFT_OCCASIONS, { it }, Modifier.fillMaxWidth()) { o -> set { it.copy(giftOccasion = o) } }
            }
        }
    }
}
