package ua.kpi.smartgrid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.R
import ua.kpi.smartgrid.data.GridStatus
import ua.kpi.smartgrid.ui.components.MessageSnackbar
import ua.kpi.smartgrid.viewmodel.MonitoringUiState
import ua.kpi.smartgrid.viewmodel.ModesViewModel

/**
 * Режими роботи мережі (екран з практичної роботи №1, тепер режим зберігає сервер).
 * Кнопки перемикання бачить лише адміністратор; оператор має доступ тільки до перегляду.
 */
@Composable
fun ModesScreen(viewModel: ModesViewModel, isAdmin: Boolean, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val status = (state as? MonitoringUiState.Success)?.status

    MessageSnackbar(message, snackbarHostState, onShown = viewModel::clearMessage)

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Режими роботи мережі", style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.height(12.dp))

            if (isAdmin) {
                // Кнопки режимів у два рядки — лише для адміністратора
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("Денний") { viewModel.setMode("Денний") }
                    ModeButton("Нічний") { viewModel.setMode("Нічний") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("Піковий") { viewModel.setMode("Піковий") }
                    ModeButton("Аварія") { viewModel.setMode("Аварія") }
                }
            } else {
                Text(
                    "Перемикання режимів доступне лише адміністратору. Ви можете переглядати поточний стан.",
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Зображення: в аварійному режимі — знак попередження
            Image(
                painter = painterResource(
                    id = if (status?.mode == "Аварія") R.drawable.ic_grid_warning else R.drawable.ic_power_grid
                ),
                contentDescription = "Стан мережі",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                is MonitoringUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is MonitoringUiState.Error -> Text("Не вдалося отримати режим з сервера",
                    color = MaterialTheme.colorScheme.error)
                is MonitoringUiState.Success -> status?.let { InfoCard(it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { viewModel.refresh() }) { Text("Оновити") }
                TextButton(onClick = onBack) { Text("Назад до меню") }
            }
        }
    }
}

/** Кнопка режиму: текст і дія передаються параметрами. */
@Composable
fun ModeButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text)
    }
}

/** Картка з інформацією про поточний стан мережі. */
@Composable
fun InfoCard(status: GridStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Режим: ${status.mode}")
            Text("Навантаження: ${"%.0f".format(status.loadKw)} кВт", fontWeight = FontWeight.Bold)
            Text("Завантаження мережі: ${"%.0f".format(status.percent)} %")
            Text("Статус: ${status.status}")
        }
    }
}
