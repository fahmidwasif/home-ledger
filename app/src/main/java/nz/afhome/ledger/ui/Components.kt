package nz.afhome.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.afhome.ledger.data.PayAccount
import nz.afhome.ledger.data.Person
import nz.afhome.ledger.data.fmtDate
import nz.afhome.ledger.data.money0
import java.time.LocalDate

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Horizontal bars with direct value labels. Single series → one hue; [colorOf] lets people keep their fixed colours. */
@Composable
fun HBars(rows: List<Pair<String, Double>>, colorOf: ((Int) -> Color)? = null, format: (Double) -> String = ::money0, max: Double? = null) {
    val chart = LocalChart.current
    val top = max ?: rows.maxOfOrNull { it.second } ?: 0.0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, (label, v) ->
            Column {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(format(v), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(3.dp))
                val c = colorOf?.invoke(i) ?: chart.bar
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    drawRoundRect(chart.grid, cornerRadius = CornerRadius(4.dp.toPx()))
                    val w = if (top > 0) (v / top).coerceIn(0.0, 1.0).toFloat() * size.width else 0f
                    if (w > 0) drawRoundRect(c, size = Size(w.coerceAtLeast(4.dp.toPx()), size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                }
            }
        }
    }
}

/** Budget-style progress: bar turns to the critical status colour when over. */
@Composable
fun ProgressRow(label: String, spent: Double, limit: Double) {
    val chart = LocalChart.current
    val over = spent > limit
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${money0(spent)} / ${money0(limit)}" + if (over) "  ⚠ over" else "", style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(3.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(chart.grid, cornerRadius = CornerRadius(4.dp.toPx()))
            val f = if (limit > 0) (spent / limit).coerceIn(0.0, 1.0).toFloat() else 0f
            drawRoundRect(if (over) chart.critical else chart.good, size = Size(f * size.width, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
        }
    }
}

/**
 * Vertical bars over time (months or weekdays). Tapping a bar shows its value; the latest bar is labelled.
 * Bars are anchored to a zero baseline with rounded tops.
 */
@Composable
fun ColumnBars(labels: List<String>, values: List<Double>, highlightLast: Boolean = true, fullLabels: List<String> = labels, format: (Double) -> String = ::money0) {
    val chart = LocalChart.current
    var selected by remember(values) { mutableIntStateOf(if (highlightLast) values.lastIndex else -1) }
    val top = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Column {
        val sel = selected
        Text(
            if (sel in values.indices) "${fullLabels[sel]}: ${format(values[sel])}" else "Tap a bar for its value",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).pointerInput(values) {
                detectTapGestures { o -> selected = (o.x / (size.width / values.size.coerceAtLeast(1))).toInt().coerceIn(0, values.lastIndex) }
            }
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val gap = 2.dp.toPx().coerceAtLeast(slot * 0.25f)
            val r = 4.dp.toPx()
            for (k in 0..2) drawLine(chart.grid, Offset(0f, size.height * k / 2f), Offset(size.width, size.height * k / 2f), 1f)
            values.forEachIndexed { i, v ->
                val h = (v / top).toFloat() * size.height
                if (h <= 0f) return@forEachIndexed
                val left = i * slot + gap / 2
                val w = slot - gap
                val topY = size.height - h
                val path = Path().apply {
                    // Rounded data end, square at the baseline.
                    val rr = minOf(r, h, w / 2)
                    moveTo(left, size.height); lineTo(left, topY + rr)
                    quadraticTo(left, topY, left + rr, topY); lineTo(left + w - rr, topY)
                    quadraticTo(left + w, topY, left + w, topY + rr); lineTo(left + w, size.height); close()
                }
                drawPath(path, if (i == sel) chart.bar else chart.barMuted)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipChoice(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun PersonChoice(selected: Person?, onSelect: (Person) -> Unit) = ChipChoice(Person.entries, selected, { it.label }, onSelect)

@Composable
fun AccountChoice(selected: PayAccount?, onSelect: (PayAccount) -> Unit) = ChipChoice(PayAccount.entries, selected, { it.label }, onSelect)

@Composable
fun <T> Dropdown(label: String, value: T, options: List<T>, text: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text(value), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Default.ArrowDropDown, null)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@Composable
fun MoneyField(label: String, value: Double?, modifier: Modifier = Modifier, onChange: (Double?) -> Unit) {
    var text by remember(value == null) { mutableStateOf(value?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; onChange(t.replace("$", "").replace(",", "").trim().toDoubleOrNull()) },
        label = { Text(label) }, prefix = { Text("$") }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
fun NumberField(label: String, value: Double?, modifier: Modifier = Modifier, suffix: String? = null, onChange: (Double?) -> Unit) {
    var text by remember(value == null) { mutableStateOf(value?.let { nz.afhome.ledger.data.qtyText(it) } ?: "") }
    OutlinedTextField(
        value = text, onValueChange = { t -> text = t; onChange(t.trim().toDoubleOrNull()) }, label = { Text(label) },
        singleLine = true, modifier = modifier, suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
fun DateButton(label: String, day: Long?, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = {
        val d = day?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
        DatePickerDialog(context, { _, y, m, dd -> onPick(LocalDate.of(y, m + 1, dd).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }) { Text("$label: ${day?.fmtDate() ?: "choose…"}") }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
