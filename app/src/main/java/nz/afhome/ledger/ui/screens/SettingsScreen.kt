package nz.afhome.ledger.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.afhome.ledger.ai.LocalLlm
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.ledger
import nz.afhome.ledger.ui.MoneyField
import nz.afhome.ledger.ui.PersonChoice
import nz.afhome.ledger.ui.SectionCard
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.ledger
    val prefs = app.prefs
    prefs.changes.collectAsState().value
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var importProgress by remember { mutableStateOf<Float?>(null) }
    val llmState by app.llm.state.collectAsState()

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            app.backup.setTarget(uri)
            status = app.backup.backupNow().fold({ "Backed up. Mischief managed ✨" }, { "Backup failed: ${it.message}" })
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { status = app.backup.exportCsv(uri).fold({ "Exported $it items." }, { "Export failed: ${it.message}" }) }
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importProgress = 0f
            status = app.llm.importModel(uri) { importProgress = it }.fold({ "Model installed: ${it.name}" }, { "Couldn't install model: ${it.message}" })
            importProgress = null
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        status?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

        item {
            SectionCard("Gringotts vault: Google Drive backup", icon = nz.afhome.ledger.ui.Magic.VaultKey) {
                Text(
                    "Your data is encrypted with a PIN and saved as one file in Google Drive (choose Drive in the file picker). " +
                        "The Drive app uploads it; this app itself has no internet access. On a new phone: install the app → Restore → pick the file → enter the PIN.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("1. Backup PIN: " + if (app.backup.hasPin) "set ✓" else "not set", modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(onClick = { pinDialog = true }) { Text(if (app.backup.hasPin) "Change PIN" else "Set PIN") }
                Text("2. Backup file: " + if (app.backup.hasTarget) "chosen ✓" else "not chosen")
                OutlinedButton(onClick = { createBackup.launch("AF-Home-backup.afhl") }, enabled = app.backup.hasPin) {
                    Text(if (app.backup.hasTarget) "Choose a different file" else "Choose where to save (Google Drive)")
                }
                if (prefs.lastBackupAt > 0) Text("Last backup: " + DateFormat.getDateTimeInstance().format(Date(prefs.lastBackupAt)), style = MaterialTheme.typography.bodySmall)
                prefs.lastBackupError?.let { Text("Last error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Back up automatically after changes", Modifier.weight(1f))
                    Switch(prefs.autoBackup, { prefs.autoBackup = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { status = app.backup.backupNow().fold({ "Backed up. Mischief managed ✨" }, { "Backup failed: ${it.message}" }) } },
                        enabled = app.backup.hasPin && app.backup.hasTarget) { Text("Back up now") }
                    FilledTonalButton(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text("Restore…") }
                }
                TextButton(onClick = { exportCsv.launch("AF-Home-purchases.csv") }) { Text("Export purchases as CSV (for Excel/Sheets)") }
            }
        }

        item {
            SectionCard("On-device AI (Gemma)", icon = nz.afhome.ledger.ui.Magic.Owl) {
                Text(
                    when (llmState) {
                        LocalLlm.State.NO_MODEL -> "No model installed. Receipt reading and insights work without it; the Owl's chat and deep analysis need it."
                        else -> "Installed: ${app.llm.modelName}"
                    }
                )
                Text(
                    "How to add it: in a browser on huggingface.co/litert-community, accept Google's Gemma licence and download ONE of:\n" +
                        "• Smarter: gemma-4-E2B-it-litert-lm → gemma-4-E2B-it.litertlm (2.6 GB, slower)\n" +
                        "• Faster: Gemma3-1B-IT → Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm (584 MB)\n" +
                        "Put it in the phone's Download folder, tap Install below, then delete the downloaded copy to free space. Gemini Nano isn't available on the GT Master, so Gemma is the local equivalent.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp),
                )
                importProgress?.let {
                    nz.afhome.ledger.ui.MagicLoading("Installing the model… ${(it * 100).toInt()}%")
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickModel.launch(arrayOf("*/*")) }, enabled = importProgress == null) { Text("Install model file…") }
                    if (llmState != LocalLlm.State.NO_MODEL) OutlinedButton(onClick = { app.llm.removeModel() }) { Text("Remove") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use GPU")
                        Text("Faster on the Snapdragon 778G, but try CPU if answers fail.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(prefs.useGpu, { prefs.useGpu = it; app.llm.unload() })
                }
            }
        }

        item {
            SectionCard("Household", icon = nz.afhome.ledger.ui.Magic.Castle) {
                Text("Who usually uses this phone?")
                PersonChoice(Person.of(prefs.defaultPerson)) { prefs.defaultPerson = it.name }
                MoneyField("Bought lunch near work costs about", prefs.lunchBoughtCost, Modifier.fillMaxWidth()) { it?.let { v -> prefs.lunchBoughtCost = v } }
                MoneyField("A packed lunch from home costs about", prefs.lunchPackedCost, Modifier.fillMaxWidth()) { it?.let { v -> prefs.lunchPackedCost = v } }
                MoneyField("Weekly grocery target", prefs.weeklyGroceryTarget, Modifier.fillMaxWidth()) { it?.let { v -> prefs.weeklyGroceryTarget = v } }
            }
        }
        item {
            Text("Privacy: this app has no internet permission. Receipt photos are deleted after reading. Only the Drive backup file leaves the phone, encrypted.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (pinDialog) PinDialog(
        title = "Backup PIN",
        message = "At least 6 digits. You'll need it to restore on a new phone, so keep it somewhere safe. It can't be recovered.",
        confirm = true,
        onDismiss = { pinDialog = false },
    ) { pin ->
        app.backup.setPin(pin)
        pinDialog = false
        if (app.backup.hasTarget) scope.launch { app.backup.backupNow() }
        status = "PIN set."
    }

    restoreUri?.let { uri ->
        PinDialog(
            title = "Restore backup",
            message = "This replaces everything on this phone with the backup. Enter the backup PIN.",
            confirm = false,
            onDismiss = { restoreUri = null },
        ) { pin ->
            restoreUri = null
            scope.launch {
                status = "Restoring…"
                status = app.backup.restore(uri, pin).fold(
                    { "Restored ${it.receipts.size} receipt${if (it.receipts.size == 1) "" else "s"} and ${it.inventory.size} stock item${if (it.inventory.size == 1) "" else "s"}. Welcome back!" },
                    { "Restore failed: ${it.message}" },
                )
            }
        }
    }
}

@Composable
private fun PinDialog(title: String, message: String, confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val valid = pin.length >= 6 && (!confirm || pin == pin2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit) }, label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (confirm) OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit) }, label = { Text("Repeat PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
        },
        confirmButton = { TextButton(onClick = { onOk(pin) }, enabled = valid) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
