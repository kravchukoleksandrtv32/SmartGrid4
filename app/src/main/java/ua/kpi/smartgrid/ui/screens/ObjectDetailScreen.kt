package ua.kpi.smartgrid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.domain.GridLabels
import ua.kpi.smartgrid.domain.ObjectFilter
import ua.kpi.smartgrid.ui.components.InfoRow
import ua.kpi.smartgrid.ui.components.ScreenTitle
import ua.kpi.smartgrid.ui.components.stateColor
import ua.kpi.smartgrid.viewmodel.ObjectsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Детальна інформація про енергооб'єкт. id приходить як аргумент навігації (маршрут object/{id}).
 * Дані читаються з Room через Flow, тому оновлення довідника одразу видно на екрані.
 */
@Composable
fun ObjectDetailScreen(id: Int, viewModel: ObjectsViewModel, onShowOnMap: () -> Unit, onBack: () -> Unit) {
    val obj by remember(id) { viewModel.objectById(id) }.collectAsState(initial = null)
    val measurements by remember(id) { viewModel.measurementsFor(id) }.collectAsState(initial = emptyList())
    val location by viewModel.userLocation.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val o = obj
        if (o == null) {
            CircularProgressIndicator()
            return@Column
        }
        val percent = GridLabels.loadPercent(o)
        val state = GridLabels.state(o)

        ScreenTitle(o.name)
        Text(state, color = stateColor(state), fontWeight = FontWeight.Bold)
        Text(o.description, modifier = Modifier.padding(vertical = 8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("Тип", GridLabels.type(o.type))
                InfoRow("Район", o.district)
                InfoRow("Статус", GridLabels.status(o.status))
                InfoRow("Номінальна потужність", "%.0f кВт".format(o.capacityKw))
                InfoRow(
                    if (o.type == "SOLAR") "Генерація" else "Навантаження",
                    "%.0f кВт (%.0f %%)".format(o.loadKw, percent)
                )
                LinearProgressIndicator(
                    progress = { (percent / 100).toFloat().coerceIn(0f, 1f) },
                    color = stateColor(state),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
                InfoRow("Координати", "%.4f, %.4f".format(o.lat, o.lon))
                // Відстань рахується, якщо відома позиція користувача (GPS)
                location?.let {
                    InfoRow("Відстань від вас", "%.1f км".format(ObjectFilter.distanceKm(it.lat, it.lon, o.lat, o.lon)))
                }
                InfoRow("Оновлено", dateFormat.format(Date(o.updatedAt)))
            }
        }

        SectionTitle("Фідери")
        o.feeders.split("; ").forEach { Text("• $it") }

        SectionTitle("Вимірювання об'єкта (${measurements.size})")
        if (measurements.isEmpty()) {
            Text("Вимірювань ще немає — їх можна додати в журналі вимірювань")
        }
        measurements.take(5).forEach { m ->
            Text("${dateFormat.format(Date(m.createdAt))}: P = %.1f кВт, ΔP = %.2f кВт".format(m.power, m.losses))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            Button(onClick = onShowOnMap) { Text("Показати на мапі") }
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }
    }
}
