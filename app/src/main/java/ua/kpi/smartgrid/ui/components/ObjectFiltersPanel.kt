package ua.kpi.smartgrid.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.domain.GridLabels
import ua.kpi.smartgrid.domain.ObjectFilter
import ua.kpi.smartgrid.domain.ObjectFilters
import ua.kpi.smartgrid.domain.SortOption
import ua.kpi.smartgrid.viewmodel.ObjectsViewModel

/**
 * Панель фільтрів: тип (можна кілька), стан, мінімальна потужність і сортування.
 * compact = true — лише тип і стан (для екрана мапи).
 */
@Composable
fun ObjectFiltersPanel(filters: ObjectFilters, viewModel: ObjectsViewModel, compact: Boolean = false) {
    Column {
        // Рівень 1: тип об'єкта
        ChipRow {
            GridLabels.types.forEach { (type, title) ->
                FilterChip(
                    selected = type in filters.types,
                    onClick = { viewModel.toggleType(type) },
                    label = { Text(title) }
                )
            }
        }

        // Рівень 2: стан роботи
        ChipRow {
            FilterChip(selected = filters.state == null, onClick = { viewModel.setState(null) },
                label = { Text("Будь-який стан") })
            ObjectFilter.states.forEach { state ->
                FilterChip(
                    selected = filters.state == state,
                    onClick = { viewModel.setState(if (filters.state == state) null else state) },
                    label = { Text(state) }
                )
            }
        }

        if (!compact) {
            // Рівень 3: номінальна потужність
            Text("Потужність від ${filters.minCapacity.toInt()} кВт", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = filters.minCapacity,
                onValueChange = viewModel::setMinCapacity,
                valueRange = 0f..2500f,
                steps = 9 // крок 250 кВт
            )

            // Сортування: стрілка показує напрямок, повторне натискання змінює його
            Text("Сортування", style = MaterialTheme.typography.labelLarge)
            ChipRow {
                SortOption.entries.forEach { option ->
                    val arrow = if (filters.sort == option) (if (filters.ascending) " ↑" else " ↓") else ""
                    FilterChip(
                        selected = filters.sort == option,
                        onClick = { viewModel.setSort(option) },
                        label = { Text(option.title + arrow) }
                    )
                }
            }
            TextButton(onClick = viewModel::resetFilters) { Text("Скинути всі фільтри") }
        }
    }
}

/** Рядок чипів з горизонтальною прокруткою. */
@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}
