package ua.kpi.smartgrid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.data.HourForecast
import ua.kpi.smartgrid.data.LoadForecastResult
import ua.kpi.smartgrid.ui.components.InfoRow
import ua.kpi.smartgrid.ui.components.LoadChart
import ua.kpi.smartgrid.ui.components.MessageSnackbar
import ua.kpi.smartgrid.ui.components.ScreenTitle
import ua.kpi.smartgrid.ui.components.stateColor
import ua.kpi.smartgrid.viewmodel.MonitoringViewModel

/**
 * Моніторинг мережі в реальному часі. Екран лише збирає StateFlow з ViewModel (collectAsState):
 * показники оновлюються кожні 2 с, графік накопичує історію, при збоях зв'язку
 * видно спроби повторного підключення. Нижче — прогноз на 12 год за погодою Open-Meteo.
 */
@Composable
fun MonitoringScreen(viewModel: MonitoringViewModel, onBack: () -> Unit) {
    val live by viewModel.liveState.collectAsState()
    val reconnect by viewModel.reconnect.collectAsState()
    val rejected by viewModel.rejected.collectAsState()
    val overloads by viewModel.overloads.collectAsState()
    val streamError by viewModel.streamError.collectAsState()
    val forecast by viewModel.forecast.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageSnackbar(message, snackbarHostState, onShown = viewModel::clearMessage)

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScreenTitle("Моніторинг мережі")
            Text("Показники оновлюються кожні 2 с", style = MaterialTheme.typography.bodySmall)

            // Стан зв'язку: повторні спроби або зупинка потоку
            reconnect?.let { ReconnectCard(it) }
            streamError?.let {
                Text("Отримання даних зупинено: $it", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Поточні показники (телеметрія + прогноз моделі)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val s = live.status
                    if (s == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("Підключення до сервера…")
                    } else {
                        InfoRow("Режим", s.mode)
                        InfoRow("Навантаження (факт)", "%.0f кВт".format(s.loadKw))
                        InfoRow("Прогноз моделі", "%.0f кВт".format(live.forecastKw))
                        InfoRow("Відхилення від прогнозу", "%+.1f %%".format(live.deviationPercent))
                        InfoRow("Частота", "%.2f Гц".format(s.frequency))
                        InfoRow("Завантаження", "%.0f %%".format(s.percent))
                        Text(s.status, color = stateColor(s.status), fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { (s.percent / 100).toFloat().coerceIn(0f, 1f) },
                            color = stateColor(s.status),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Навантаження в реальному часі", fontWeight = FontWeight.Bold)
                    LoadChart(live.history, modifier = Modifier.padding(top = 8.dp))
                    InfoRow("Відкинуто збійних показників", rejected.toString())
                    InfoRow("Перевантажень за сеанс", overloads.toString())
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ForecastCard(forecast)

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.loadForecast() }) { Text("Оновити прогноз") }
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
        }
    }
}

/** Повідомлення про втрату зв'язку і повторне підключення. */
@Composable
fun ReconnectCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Прогноз навантаження за погодою: температура, прогноз на кожну годину, попередження. */
@Composable
fun ForecastCard(forecast: LoadForecastResult?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Прогноз навантаження (погода Open-Meteo)", fontWeight = FontWeight.Bold)
            if (forecast == null) {
                Text("Прогноз ще не отримано")
                return@Column
            }
            Text("Зараз у Києві: %.1f °C, вітер %.0f км/год".format(forecast.temperature, forecast.windSpeed))

            if (forecast.overloadHours.isNotEmpty()) {
                Text(
                    "Очікується перевантаження: ${forecast.overloadHours.joinToString()}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            forecast.hours.forEach { ForecastRow(it) }
        }
    }
}

@Composable
fun ForecastRow(hour: HourForecast) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)) {
        Text(hour.time, modifier = Modifier.weight(1f))
        Text("%.1f °C".format(hour.temperature), modifier = Modifier.weight(1f))
        Text("%.0f кВт".format(hour.loadKw), modifier = Modifier.weight(1.2f))
        Text(
            "%.0f %%".format(hour.percent),
            color = if (hour.percent > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}
