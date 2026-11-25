package ua.kpi.smartgrid.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import ua.kpi.smartgrid.domain.GridLabels
import ua.kpi.smartgrid.domain.ObjectItem
import ua.kpi.smartgrid.location.LocationProvider
import ua.kpi.smartgrid.ui.components.MessageSnackbar
import ua.kpi.smartgrid.ui.components.ObjectCard
import ua.kpi.smartgrid.ui.components.ObjectFiltersPanel
import ua.kpi.smartgrid.ui.components.UserLocationEffect
import ua.kpi.smartgrid.viewmodel.ObjectsViewModel

private val KYIV = LatLng(50.45, 30.52)

/**
 * Мапа енергооб'єктів (Google Maps для Compose) і поточна позиція користувача (GPS).
 * Список під мапою синхронізований з маркерами:
 * вибір картки — камера переходить до маркера і відкриває InfoWindow;
 * натискання на маркер — виділяється і прокручується відповідна картка.
 * На мапі показуються лише об'єкти, що пройшли фільтри.
 */
@Composable
fun MapScreen(
    viewModel: ObjectsViewModel,
    locationProvider: LocationProvider,
    onOpenDetails: (Int) -> Unit,
    onBack: () -> Unit
) {
    val objects by viewModel.items.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageSnackbar(message, snackbarHostState, onShown = viewModel::clearMessage)

    // ---------- Дозвіл на геолокацію ----------
    var hasPermission by remember { mutableStateOf(locationProvider.hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result.values.any { it } }

    LaunchedEffect(Unit) {
        viewModel.refresh(force = false)
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
    val locationEnabled = remember(hasPermission) { locationProvider.isLocationEnabled() }

    // Позиція користувача оновлюється, поки екран відкритий
    UserLocationEffect(locationProvider, enabled = hasPermission, onLocation = viewModel::updateLocation)

    // ---------- Стан мапи і списку ----------
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(KYIV, 10.5f)
    }
    // Стан маркера для кожного об'єкта (створюється заново лише при зміні набору об'єктів)
    val markerStates = remember(objects.map { it.obj.id }) {
        objects.associate { it.obj.id to MarkerState(position = LatLng(it.obj.lat, it.obj.lon)) }
    }
    val listState = rememberLazyListState()

    // Синхронізація: при виборі об'єкта — прокрутка списку, переміщення камери і InfoWindow.
    // Ключ markerStates — щоб ефект спрацював і тоді, коли список завантажився після вибору.
    LaunchedEffect(selectedId, markerStates) {
        val id = selectedId ?: return@LaunchedEffect
        val index = objects.indexOfFirst { it.obj.id == id }
        if (index < 0) return@LaunchedEffect // об'єкт приховано фільтрами
        val obj = objects[index].obj
        listState.animateScrollToItem(index)
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(obj.lat, obj.lon), 14f), 800)
        markerStates[id]?.showInfoWindow()
    }

    // Найближчий до користувача об'єкт
    val nearest = objects.filter { it.distanceKm != null }.minByOrNull { it.distanceKm ?: Double.MAX_VALUE }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Мапа енергооб'єктів", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Назад") }
            }

            // Повідомлення про відсутність дозволу або вимкнену геолокацію
            when {
                !hasPermission -> PermissionCard {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
                !locationEnabled -> Text(
                    "Геолокацію вимкнено в налаштуваннях пристрою — позицію не визначено.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                nearest != null -> Text(
                    "Найближчий об'єкт: ${nearest.obj.name} — %.1f км".format(nearest.distanceKm),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasPermission), // синя точка — позиція користувача
                uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = hasPermission),
                onMapClick = { viewModel.select(null) }
            ) {
                objects.forEach { item ->
                    val state = markerStates[item.obj.id] ?: return@forEach
                    val isSelected = item.obj.id == selectedId
                    // key — маркер «прив'язаний» до свого об'єкта, навіть якщо порядок у списку змінився
                    key(item.obj.id) {
                        Marker(
                            state = state,
                            title = item.obj.name,
                            snippet = "${GridLabels.type(item.obj.type)} · ${"%.0f".format(item.obj.loadKw)} кВт · ${item.state}",
                            icon = BitmapDescriptorFactory.defaultMarker(markerHue(item, isSelected)),
                            zIndex = if (isSelected) 1f else 0f,
                            onClick = {
                                viewModel.select(item.obj.id)
                                false // false — стандартна поведінка: InfoWindow і центрування
                            },
                            onInfoWindowClick = { onOpenDetails(item.obj.id) }
                        )
                    }
                }
            }

            // Фільтри діють одночасно на список і маркери
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                ObjectFiltersPanel(filters, viewModel, compact = true)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(objects, key = { it.obj.id }) { item ->
                    ObjectCard(
                        item = item,
                        selected = item.obj.id == selectedId,
                        onClick = { viewModel.select(item.obj.id) },
                        expandedContent = {
                            TextButton(onClick = { onOpenDetails(item.obj.id) }) { Text("Детальніше") }
                        }
                    )
                }
            }
        }
    }
}

/** Картка з поясненням, навіщо потрібен дозвіл, і кнопкою повторного запиту. */
@Composable
fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Доступ до геолокації не надано. Об'єкти показано, але ваша позиція і відстань невідомі.")
            TextButton(onClick = onRequest) { Text("Надати доступ") }
        }
    }
}

// Колір маркера: вибраний — блакитний, далі за станом об'єкта
private fun markerHue(item: ObjectItem, selected: Boolean): Float = when {
    selected -> BitmapDescriptorFactory.HUE_AZURE
    item.state == "Норма" -> BitmapDescriptorFactory.HUE_GREEN
    item.state == "Високе навантаження" -> BitmapDescriptorFactory.HUE_ORANGE
    item.state == "Обслуговування" -> BitmapDescriptorFactory.HUE_YELLOW
    else -> BitmapDescriptorFactory.HUE_RED
}
