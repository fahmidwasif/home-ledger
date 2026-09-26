package nz.afhome.ledger.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nz.afhome.ledger.ai.Assistant
import nz.afhome.ledger.ai.LocalLlm
import nz.afhome.ledger.ledger

data class ChatLine(val fromUser: Boolean, val text: String)

class AskViewModel(app: Application) : AndroidViewModel(app) {
    private val ledger = app.ledger
    val lines = mutableStateListOf<ChatLine>()
    var busy by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun ask(question: String) {
        if (question.isBlank() || busy) return
        lines += ChatLine(true, question.trim())
        busy = true
        job = viewModelScope.launch {
            try {
                // "Where is the rice?" is answered exactly from the inventory: no model needed and no guessing.
                val quick = ledger.assistant.quickAnswer(question)
                val hasModel = ledger.llm.state.value != LocalLlm.State.NO_MODEL
                when {
                    quick != null && (!hasModel || question.lowercase().startsWith("where")) -> lines += ChatLine(false, quick)
                    !hasModel -> lines += ChatLine(false,
                        (quick?.let { "$it\n\n" } ?: "") +
                            "For open questions I need the on-device AI model. Add it in Settings → On-device AI (one-time, ~550 MB). " +
                            "Until then I can answer \"where is…\" and \"do we have…\" questions from the home stock.")
                    else -> {
                        val idx = lines.size
                        lines += ChatLine(false, "…")
                        val sb = StringBuilder()
                        ledger.llm.stream(ledger.assistant.context(question), question).collect { piece ->
                            sb.append(piece)
                            lines[idx] = ChatLine(false, sb.toString())
                        }
                        if (sb.isBlank()) lines[idx] = ChatLine(false, "(no answer)")
                    }
                }
            } catch (t: Throwable) {
                if (lines.lastOrNull()?.text == "…") lines.removeAt(lines.lastIndex)
                lines += ChatLine(false, "Sorry, the Owl couldn't answer: ${t.message}")
            } finally {
                busy = false
            }
        }
    }

    fun clear() { job?.cancel(); lines.clear(); busy = false }
}

@Composable
fun AskScreen(vm: AskViewModel = viewModel()) {
    val llm = androidx.compose.ui.platform.LocalContext.current.ledger.llm
    val state by llm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.lines.size, vm.lines.lastOrNull()?.text?.length) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem(vm.lines.size)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(nz.afhome.ledger.ui.Magic.Owl, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 10.dp).size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ask the Owl", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (state) {
                                LocalLlm.State.NO_MODEL -> "AI model not installed: inventory questions only"
                                LocalLlm.State.LOADING -> "Waking the Owl… (loading the model)"
                                LocalLlm.State.ERROR -> "Model failed to load: ${llm.lastError ?: ""}"
                                else -> "Runs fully on this phone. Nothing is sent anywhere."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (vm.lines.isNotEmpty()) TextButton(onClick = vm::clear) { Text("Clear") }
                }
            }
            if (vm.lines.isEmpty()) {
                item { Text("Try asking:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
                items(Assistant.SUGGESTED) { s -> SuggestionChip(onClick = { vm.ask(s) }, label = { Text(s) }) }
            }
            items(vm.lines) { line -> Bubble(line) }
            if (vm.busy) item {
                nz.afhome.ledger.ui.MagicLoading(
                    if (state == LocalLlm.State.LOADING) "Waking the Owl… loading the model (first time ~20s)" else "The Owl is thinking…",
                    Modifier.padding(top = 4.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("Ask about spending, stock, recipes…") },
                modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(onClick = { vm.ask(input); input = "" }, enabled = input.isNotBlank() && !vm.busy) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (line.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) { Text(line.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
    }
}
