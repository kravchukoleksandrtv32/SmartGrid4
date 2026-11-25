package ua.kpi.smartgrid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.data.auth.Session
import ua.kpi.smartgrid.ui.components.InfoRow
import ua.kpi.smartgrid.ui.components.MenuButton
import ua.kpi.smartgrid.ui.components.ScreenTitle
import ua.kpi.smartgrid.viewmodel.MeasurementViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Головне меню. Ім'я та роль беруться з токена (Session).
 * Пункт «Адміністрування» бачить лише адміністратор — інтерфейс залежить від ролі.
 */
@Composable
fun MainMenuScreen(
    session: Session?,
    viewModel: MeasurementViewModel,
    onMonitoring: () -> Unit,
    onObjects: () -> Unit,
    onMap: () -> Unit,
    onModes: () -> Unit,
    onCalculator: () -> Unit,
    onMeasurements: () -> Unit,
    onNewMeasurement: () -> Unit,
    onAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val summary by viewModel.summary.collectAsState()
    val expiresAt = session?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.expiresAt)) }

    // Пункти меню; адміністративний додається лише для ролі ADMIN
    val items = buildList<Pair<String, () -> Unit>> {
        add("Моніторинг мережі" to onMonitoring)
        add("Енергооб'єкти" to onObjects)
        add("Мапа об'єктів" to onMap)
        add("Режими мережі" to onModes)
        add("Розрахунок лінії" to onCalculator)
        add("Журнал вимірювань" to onMeasurements)
        add("Нове вимірювання" to onNewMeasurement)
        if (session?.isAdmin == true) add("Адміністрування" to onAdmin)
    }

    // BoxWithConstraints — щоб знати ширину екрана і зробити адаптивне меню
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth > 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScreenTitle("Вітаємо, ${session?.name ?: "оператор"}!")
            Text("Роль: ${session?.roleTitle ?: "—"} · сесія до ${expiresAt ?: "—"}",
                style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(12.dp))

            // Зведення з бази даних
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("Записів у журналі", summary.count.toString())
                    InfoRow("Середня потужність", "%.1f кВт".format(summary.averagePower))
                    InfoRow("Перевантажень", summary.overloadCount.toString())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isWide) {
                // Широкий екран: кнопки у два стовпці
                items.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { (title, action) -> MenuButton(title, Modifier.weight(1f), action) }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                // Телефон: кнопки одна під одною
                items.forEach { (title, action) -> MenuButton(title, onClick = action) }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onLogout) { Text("Вийти") }
        }
    }
}
