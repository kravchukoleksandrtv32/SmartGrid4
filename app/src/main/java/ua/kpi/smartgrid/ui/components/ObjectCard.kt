package ua.kpi.smartgrid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.domain.GridLabels
import ua.kpi.smartgrid.domain.ObjectItem
import ua.kpi.smartgrid.ui.theme.StatusError
import ua.kpi.smartgrid.ui.theme.StatusIdle
import ua.kpi.smartgrid.ui.theme.StatusOk
import ua.kpi.smartgrid.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Картка енергооб'єкта для списків LazyColumn.
 * Вибрана картка виділяється рамкою і показує додатковий вміст (expandedContent).
 */
@Composable
fun ObjectCard(
    item: ObjectItem,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    expandedContent: (@Composable () -> Unit)? = null
) {
    val obj = item.obj
    val color = stateColor(item.state)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(obj.updatedAt))
    val label = if (obj.type == "SOLAR") "Генерація" else "Навантаження"
    val distance = item.distanceKm?.let { " · %.1f км від вас".format(it) }.orEmpty()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(obj.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(item.state, color = color, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "${GridLabels.type(obj.type)} · ${obj.district} район$distance",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "$label: ${"%.0f".format(obj.loadKw)} з ${"%.0f".format(obj.capacityKw)} кВт (${"%.0f".format(item.percent)} %)",
                modifier = Modifier.padding(top = 4.dp)
            )
            LinearProgressIndicator(
                progress = { (item.percent / 100).toFloat().coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            Text("Оновлено о $time", style = MaterialTheme.typography.bodySmall)

            // Додаткові параметри вибраного об'єкта
            if (selected && expandedContent != null) expandedContent()
        }
    }
}

/** Колір стану об'єкта. */
fun stateColor(state: String): Color = when (state) {
    "Норма" -> StatusOk
    "Високе навантаження" -> StatusWarning
    "Перевантаження", "Відключено" -> StatusError
    else -> StatusIdle
}
