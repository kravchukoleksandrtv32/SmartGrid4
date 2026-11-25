package ua.kpi.smartgrid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.data.ObjectsSummary
import ua.kpi.smartgrid.domain.GridLabels
import ua.kpi.smartgrid.domain.ObjectItem
import ua.kpi.smartgrid.location.LocationProvider
import ua.kpi.smartgrid.ui.components.InfoRow
import ua.kpi.smartgrid.ui.components.LoadingButton
import ua.kpi.smartgrid.ui.components.MessageSnackbar
import ua.kpi.smartgrid.ui.components.ObjectCard
import ua.kpi.smartgrid.ui.components.ObjectFiltersPanel
import ua.kpi.smartgrid.ui.components.ScreenTitle
import ua.kpi.smartgrid.ui.components.UserLocationEffect
import ua.kpi.smartgrid.viewmodel.ObjectsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Енергооб'єкти мережі: динамічний список LazyColumn з картками,
 * пошуком за назвою, багаторівневою фільтрацією і сортуванням.
 * Натискання на картку виділяє її і показує додаткові параметри та переходи.
 */
@Composable
fun ObjectsScreen(
    viewModel: ObjectsViewModel,
    locationProvider: LocationProvider,
    onOpenDetails: (Int) -> Unit,
    onOpenMap: () -> Unit,
    onBack: () -> Unit
) {
    val objects by viewModel.items.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val query by viewModel.query.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // Довідник: з кешу, якщо він свіжий, інакше з сервера
    LaunchedEffect(Unit) { viewModel.refresh(force = false) }

    // Позиція користувача для відстані до об'єктів (якщо дозвіл уже надано)
    UserLocationEffect(locationProvider, enabled = locationProvider.hasPermission(), onLocation = viewModel::updateLocation)

    MessageSnackbar(message, snackbarHostState, onShown = viewModel::clearMessage,
        actionLabel = "Повторити", onAction = { viewModel.refresh() })

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ScreenTitle("Енергооб'єкти мережі")

            if (lastSync > 0) {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSync))
                Text(
                    if (isOffline) "Офлайн-режим: збережені дані від $time" else "Синхронізовано о $time",
                    color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Пошук за назвою: список оновлюється автоматично (з затримкою debounce у ViewModel)
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                label = { Text("Пошук за назвою") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.query.value = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистити")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showFilters = !showFilters }) {
                    Text(if (showFilters) "Сховати фільтри ▲" else "Фільтри і сортування ▼")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Знайдено ${objects.size} з $totalCount", style = MaterialTheme.typography.bodySmall)
            }

            if (showFilters) ObjectFiltersPanel(filters, viewModel)

            // Порожній результат — підказка користувачу
            if (objects.isEmpty() && !isLoading) {
                Text(
                    if (totalCount == 0) "Немає даних. Перевірте з'єднання і натисніть «Оновити»."
                    else "Немає об'єктів, що відповідають умовам пошуку.",
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                if (totalCount > 0) TextButton(onClick = viewModel::resetFilters) { Text("Скинути фільтри") }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item { SummaryCard(summary) }
                // key — стабільний ідентифікатор: Compose не перемальовує картки, які не змінились
                items(objects, key = { it.obj.id }) { item ->
                    ObjectCard(
                        item = item,
                        selected = item.obj.id == selectedId,
                        onClick = { viewModel.toggleSelection(item.obj.id) },
                        expandedContent = {
                            SelectedObjectInfo(
                                item = item,
                                onDetails = { onOpenDetails(item.obj.id) },
                                onMap = { viewModel.select(item.obj.id); onOpenMap() }
                            )
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    LoadingButton("Оновити", isLoading) { viewModel.refresh(force = true) }
                }
                OutlinedButton(onClick = onOpenMap, modifier = Modifier.height(48.dp)) { Text("Мапа") }
                OutlinedButton(onClick = onBack, modifier = Modifier.height(48.dp)) { Text("Назад") }
            }
        }
    }
}

/** Додаткові параметри вибраного об'єкта в розгорнутій картці. */
@Composable
fun SelectedObjectInfo(item: ObjectItem, onDetails: () -> Unit, onMap: () -> Unit) {
    val obj = item.obj
    Column(modifier = Modifier.padding(top = 4.dp)) {
        HorizontalDivider()
        Text(obj.description, modifier = Modifier.padding(vertical = 4.dp))
        Text(
            "Статус: ${GridLabels.status(obj.status)} · фідерів: ${obj.feeders.split("; ").size}",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Координати: %.4f, %.4f".format(obj.lat, obj.lon), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = onDetails) { Text("Детальніше") }
            OutlinedButton(onClick = onMap) { Text("На мапі") }
        }
    }
}

/** Агреговані показники, які обчислює репозиторій. */
@Composable
fun SummaryCard(summary: ObjectsSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            InfoRow("Об'єктів", summary.count.toString())
            InfoRow("Навантаження споживачів", "%.0f з %.0f кВт (%.0f %%)".format(
                summary.loadKw, summary.capacityKw, summary.loadPercent))
            InfoRow("Генерація СЕС", "%.0f кВт".format(summary.generationKw))
            InfoRow("Перевантажені / не працюють", "${summary.overloaded} / ${summary.notWorking}")
        }
    }
}
