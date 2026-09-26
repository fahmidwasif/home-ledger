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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.LineItem
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.Receipt
import nz.afhome.ledger.data.fmtDate
import nz.afhome.ledger.data.money
import nz.afhome.ledger.data.qtyText
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.SectionCard

@Composable
fun ReceiptsScreen(onOpen: (Long) -> Unit) {
    val app = LocalContext.current.ledger
    val list by app.repo.dao.receiptsFlow().collectAsState(initial = emptyList())
    var q by remember { mutableStateOf("") }
    val shown = list.filter { q.isBlank() || it.store.contains(q, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { OutlinedTextField(q, { q = it }, placeholder = { Text("Search shops…") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        shown.groupBy { it.date.fmtDate().substringAfter(' ') }.forEach { (month, rs) ->
            item {
                Row(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                    Text(month, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text(money(rs.sumOf { it.total }), style = MaterialTheme.typography.titleSmall)
                }
            }
            items(rs, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(vertical = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.store, fontWeight = FontWeight.Medium)
                        Text("${r.date.fmtDate()} · ${Person.of(r.purchaser).label} · ${r.itemCount} items", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(money(r.total))
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ReceiptDetailScreen(id: Long, onDeleted: () -> Unit) {
    val app = LocalContext.current.ledger
    val scope = rememberCoroutineScope()
    var receipt by remember { mutableStateOf<Receipt?>(null) }
    var items by remember { mutableStateOf<List<LineItem>>(emptyList()) }
    var confirm by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    LaunchedEffect(id) { receipt = app.repo.dao.receipt(id); items = app.repo.dao.itemsOf(id) }
    val r = receipt ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(r.store, icon = nz.afhome.ledger.ui.Magic.Diary) {
                Text(r.date.fmtDate())
                Text("Bought by ${Person.of(r.purchaser).label} · paid from ${PayAccount.of(r.payment)?.label ?: "not recorded"}")
                Text("Total ${money(r.total)}" + (r.gst?.let { " · incl. GST ${money(it)}" } ?: ""), fontWeight = FontWeight.SemiBold)
                r.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        items(items, key = { it.id }) { li ->
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text((if (li.isGift) "🎁 " else "") + li.name)
                    Text(listOfNotNull(Category.of(li.category).label, "${qtyText(li.qty)} × ${money(li.unitPrice)}",
                        li.giftFor?.let { "for $it" }, li.giftOccasion).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Text(money(li.total))
            }
        }
        item {
            Row {
                if (r.rawText != null) TextButton(onClick = { showText = true }) { Text("Show scanned text") }
                TextButton(onClick = { confirm = true }) { Text("Delete receipt", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (showText) AlertDialog(onDismissRequest = { showText = false }, confirmButton = { TextButton(onClick = { showText = false }) { Text("Close") } },
        title = { Text("Scanned text") }, text = { Text(r.rawText ?: "", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) })
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete this receipt?") },
        text = { Text("Its items are removed from spending history. Home stock isn't changed.") },
        confirmButton = { TextButton(onClick = { scope.launch { app.repo.deleteReceipt(id); onDeleted() } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
