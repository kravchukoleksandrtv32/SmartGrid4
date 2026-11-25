package ua.kpi.smartgrid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import ua.kpi.smartgrid.domain.LoadForecast
import ua.kpi.smartgrid.viewmodel.LivePoint
import ua.kpi.smartgrid.viewmodel.MonitoringViewModel

/**
 * Простий лінійний графік на Canvas: фактичне навантаження, прогноз моделі
 * і пунктир допустимого навантаження мережі. Нові точки додаються праворуч.
 */
@Composable
fun LoadChart(points: List<LivePoint>, modifier: Modifier = Modifier) {
    val loadColor = MaterialTheme.colorScheme.primary
    val forecastColor = MaterialTheme.colorScheme.tertiary
    val limitColor = MaterialTheme.colorScheme.error

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val maxValue = maxOf(
            LoadForecast.GRID_CAPACITY_KW,
            points.maxOfOrNull { maxOf(it.loadKw, it.forecastKw) } ?: 0.0
        ) * 1.1
        val stepX = size.width / (MonitoringViewModel.MAX_POINTS - 1)

        // Перетворення значення (кВт) у координату Y
        fun y(value: Double): Float = size.height - (value / maxValue * size.height).toFloat()

        // Допустиме навантаження — червоний пунктир
        val limitY = y(LoadForecast.GRID_CAPACITY_KW)
        drawLine(
            color = limitColor,
            start = Offset(0f, limitY),
            end = Offset(size.width, limitY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        )

        // Лінії прогнозу і фактичного навантаження
        for (i in 1 until points.size) {
            val x0 = (i - 1) * stepX
            val x1 = i * stepX
            drawLine(forecastColor, Offset(x0, y(points[i - 1].forecastKw)), Offset(x1, y(points[i].forecastKw)), 4f)
            drawLine(loadColor, Offset(x0, y(points[i - 1].loadKw)), Offset(x1, y(points[i].loadKw)), 6f)
        }
    }

    // Легенда
    Row(modifier = Modifier.padding(top = 4.dp)) {
        LegendItem("факт", loadColor)
        LegendItem("прогноз", forecastColor)
        LegendItem("допустиме", limitColor)
    }
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Text("● ", color = color)
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp))
}
