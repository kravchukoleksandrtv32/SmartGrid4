package ua.kpi.smartgrid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ua.kpi.smartgrid.location.LocationProvider

/**
 * Збирає оновлення позиції користувача, поки екран відкритий і є дозвіл.
 * Коли екран закривається, LaunchedEffect скасовує корутину, і потік знімає підписку на GPS.
 */
@Composable
fun UserLocationEffect(provider: LocationProvider, enabled: Boolean, onLocation: (lat: Double, lon: Double) -> Unit) {
    LaunchedEffect(enabled) {
        if (enabled) {
            provider.locationUpdates().collect { location -> onLocation(location.latitude, location.longitude) }
        }
    }
}
