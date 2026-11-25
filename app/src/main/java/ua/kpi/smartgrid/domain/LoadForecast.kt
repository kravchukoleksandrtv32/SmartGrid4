package ua.kpi.smartgrid.domain

/**
 * Модель прогнозу навантаження мережі за годиною доби і температурою повітря.
 * Прогноз = пікове навантаження × частка за добовим графіком × температурний коефіцієнт.
 */
object LoadForecast {

    const val GRID_CAPACITY_KW = 10000.0 // допустиме навантаження мережі
    private const val PEAK_LOAD_KW = 8500.0

    // Типовий добовий графік навантаження (частка від пікового) для годин 0..23
    private val dailyProfile = listOf(
        0.45, 0.42, 0.40, 0.40, 0.42, 0.50, 0.62, 0.75, 0.82, 0.85, 0.84, 0.83,
        0.82, 0.80, 0.80, 0.82, 0.86, 0.93, 1.00, 0.98, 0.92, 0.80, 0.65, 0.52
    )

    // Нижче +18 °C вмикається опалення (+1,5 % за кожен градус), вище +25 °C — кондиціонери (+1 %)
    fun temperatureFactor(temperature: Double): Double = when {
        temperature < 18 -> 1 + (18 - temperature) * 0.015
        temperature > 25 -> 1 + (temperature - 25) * 0.01
        else -> 1.0
    }

    fun forecast(hour: Int, temperature: Double): Double =
        PEAK_LOAD_KW * dailyProfile[hour.coerceIn(0, 23)] * temperatureFactor(temperature)
}
