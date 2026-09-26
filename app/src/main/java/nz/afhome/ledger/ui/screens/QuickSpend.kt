package nz.afhome.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.today
import nz.afhome.ledger.ui.AccountChoice
import nz.afhome.ledger.ui.DateButton
import nz.afhome.ledger.ui.Dropdown
import nz.afhome.ledger.ui.MoneyField
import nz.afhome.ledger.ui.PersonChoice

/** One-tap shortcuts on Home for spending that rarely has a paper receipt. */
enum class QuickPreset(val label: String, val what: String, val category: Category) {
    AT_HOP("AT HOP top-up", "AT HOP top-up", Category.TRANSPORT),
    COFFEE("Coffee / snack", "Coffee", Category.DINING),
    LUNCH("Bought lunch", "Lunch at work", Category.WORK_LUNCH),
    TAKEAWAY("Takeaway", "Takeaway", Category.DINING),
    GADGET("Gadget", "", Category.ELECTRONICS),
    SUBSCRIPTION("App / subscription", "", Category.SUBSCRIPTIONS),
    UBER("Uber / taxi", "Uber", Category.TAXI),
    PARKING("Parking", "Parking", Category.PARKING),
    BILL("Bill", "", Category.UTILITIES),
    OTHER("Something else", "", Category.OTHER),
}

@Composable
fun QuickSpendDialog(
    preset: QuickPreset,
    defaultPerson: Person,
    lastAccount: (Person) -> PayAccount?,
    onDismiss: () -> Unit,
    onSave: (what: String, amount: Double, category: Category, person: Person, account: PayAccount?, day: Long) -> Unit,
) {
    var what by remember { mutableStateOf(preset.what) }
    var amount by remember { mutableStateOf<Double?>(null) }
    var category by remember { mutableStateOf(preset.category) }
    var person by remember { mutableStateOf(defaultPerson) }
    var account by remember { mutableStateOf(lastAccount(defaultPerson)) }
    var day by remember { mutableStateOf(today()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(what, { what = it }, label = { Text("What was it?") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (preset == QuickPreset.GADGET) "e.g. AirPods, phone charger" else "e.g. Netflix, Spark mobile") })
                MoneyField("Amount", amount, Modifier.fillMaxWidth()) { amount = it }
                Dropdown("Category", category, Category.entries, { it.label }, Modifier.fillMaxWidth()) { category = it }
                Text("Who paid?", style = MaterialTheme.typography.labelLarge)
                PersonChoice(person) { person = it; account = account ?: lastAccount(it) }
                Text("Paid from", style = MaterialTheme.typography.labelLarge)
                AccountChoice(account) { account = it }
                DateButton("Date", day) { day = it }
                if (preset == QuickPreset.SUBSCRIPTION) Text(
                    "If it repeats every month, add it under Subscriptions instead. It will then be recorded automatically.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(what.trim().ifEmpty { category.label }, amount!!, category, person, account, day) },
                enabled = (amount ?: 0.0) > 0 && account != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
