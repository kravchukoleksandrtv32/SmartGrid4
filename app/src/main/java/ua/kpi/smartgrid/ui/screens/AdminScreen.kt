package ua.kpi.smartgrid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.ui.components.MessageSnackbar
import ua.kpi.smartgrid.ui.components.ScreenTitle
import ua.kpi.smartgrid.viewmodel.AdminViewModel

/**
 * Адміністрування (доступне лише ролі ADMIN):
 * користувачі з захищеного ресурсу, перевірка обробки помилок і журнал помилок.
 */
@Composable
fun AdminScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val users by viewModel.users.collectAsState()
    val log by viewModel.log.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageSnackbar(message, snackbarHostState, onShown = viewModel::clearMessage)

    // Кнопки перевірки: що надіслати на сервер і яку помилку очікуємо
    val checks = listOf(
        "no_token" to "Без токена (401)",
        "bad_token" to "Недійсний токен (401)",
        "404" to "Ресурс не існує (404)",
        "500" to "Помилка сервера (500)",
        "timeout" to "Тайм-аут"
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                ScreenTitle("Адміністрування")
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            item { SectionTitle("Користувачі системи") }
            items(users) { user ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.Bold)
                            Text(user.email, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (user.role == "ADMIN") "Адміністратор" else "Оператор",
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item { SectionTitle("Перевірка обробки помилок") }
            item {
                Column {
                    checks.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (kind, title) ->
                                OutlinedButton(
                                    onClick = { viewModel.runDiagnostic(kind) },
                                    enabled = !isLoading,
                                    modifier = Modifier.weight(1f)
                                ) { Text(title, style = MaterialTheme.typography.labelSmall) }
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Журнал помилок (${log.size})", Modifier.weight(1f))
                    TextButton(onClick = { viewModel.loadLog() }) { Text("Оновити") }
                    TextButton(onClick = { viewModel.clearLog() }) { Text("Очистити") }
                }
            }
            if (log.isEmpty()) item { Text("Помилок не зафіксовано") }
            items(log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }

            item {
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Назад до меню") }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp)
    )
}
