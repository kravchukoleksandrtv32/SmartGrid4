package ua.kpi.smartgrid.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ua.kpi.smartgrid.data.local.GridObjectEntity
import ua.kpi.smartgrid.data.local.LocalDataSource
import ua.kpi.smartgrid.data.local.MeasurementEntity
import ua.kpi.smartgrid.data.local.MeasurementWithObject
import ua.kpi.smartgrid.data.remote.GridObjectDto
import ua.kpi.smartgrid.data.remote.GridStatusDto
import ua.kpi.smartgrid.data.remote.RemoteDataSource
import ua.kpi.smartgrid.data.remote.WeatherResponse
import ua.kpi.smartgrid.domain.Calculator
import ua.kpi.smartgrid.domain.GridLabels
import ua.kpi.smartgrid.domain.LoadForecast
import java.time.LocalTime

// ---------- Моделі, які репозиторій віддає у ViewModel ----------

data class MeasurementSummary(
    val count: Int = 0,
    val averagePower: Double = 0.0,
    val maxPower: Double = 0.0,
    val overloadCount: Int = 0
)

data class ObjectsSummary(
    val count: Int = 0,
    val capacityKw: Double = 0.0,
    val loadKw: Double = 0.0,
    val generationKw: Double = 0.0, // генерація СЕС
    val overloaded: Int = 0,
    val notWorking: Int = 0         // відключені або на обслуговуванні
) {
    val loadPercent: Double get() = Calculator.calculateLoadPercent(loadKw, capacityKw)
}

data class GridStatus(
    val mode: String,
    val loadKw: Double,
    val frequency: Double,
    val percent: Double,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    // Показник датчика коректний, якщо частота в межах 45–55 Гц
    val isValid: Boolean get() = frequency in 45.0..55.0 && loadKw >= 0
}

data class HourForecast(val time: String, val temperature: Double, val loadKw: Double, val percent: Double)

data class LoadForecastResult(
    val temperature: Double,
    val windSpeed: Double,
    val hours: List<HourForecast>,
    val overloadHours: List<String> // години, коли прогноз перевищує допустиме навантаження
)

/**
 * Репозиторій — єдина точка доступу до даних для ViewModel.
 * Поєднує локальне (Room) і віддалене (REST API) джерела, вирішує, звідки брати дані,
 * кешує довідник об'єктів і виконує бізнес-логіку (агрегація, прогноз навантаження).
 */
class GridRepository(
    private val local: LocalDataSource,
    private val remote: RemoteDataSource
) {

    // ---------- Енергооб'єкти: кеш у Room + сервер ----------

    // UI завжди читає дані з бази, тому список доступний і без мережі (офлайн-режим)
    val objects: Flow<List<GridObjectEntity>> = local.objects

    /**
     * Оновлення довідника. Якщо кеш свіжий (молодший за 5 хв) і оновлення не примусове —
     * запит до сервера не робимо. Повертає true, якщо дані прийшли з сервера.
     * При помилці кеш залишається, а ViewModel показує повідомлення.
     */
    suspend fun refreshObjects(force: Boolean = false): Result<Boolean> {
        val cacheAge = System.currentTimeMillis() - local.lastSyncTime()
        if (!force && local.objectsCount() > 0 && cacheAge < CACHE_TTL) {
            return Result.success(false)
        }
        return remote.getObjects().map { list ->
            val now = System.currentTimeMillis()
            local.saveObjects(list.map { it.toEntity(now) })
            true
        }
    }

    suspend fun lastSyncTime(): Long = local.lastSyncTime()

    fun objectById(id: Int): Flow<GridObjectEntity?> = local.objectById(id)

    fun measurementsForObject(id: Int): Flow<List<MeasurementEntity>> = local.measurementsForObject(id)

    // Агрегація показників по всіх об'єктах
    fun summarizeObjects(list: List<GridObjectEntity>): ObjectsSummary {
        val working = list.filter { it.status == "ONLINE" }
        val consumers = working.filter { it.type != "SOLAR" }
        return ObjectsSummary(
            count = list.size,
            capacityKw = consumers.sumOf { it.capacityKw },
            loadKw = consumers.sumOf { it.loadKw },
            generationKw = working.filter { it.type == "SOLAR" }.sumOf { it.loadKw },
            overloaded = consumers.count { GridLabels.loadPercent(it) > 100 },
            notWorking = list.size - working.size
        )
    }

    // ---------- Стан мережі та режими ----------

    suspend fun getGridStatus(): Result<GridStatus> = remote.getStatus().map { it.toGridStatus() }

    suspend fun setMode(mode: String): Result<GridStatus> = remote.setMode(mode).map { it.toGridStatus() }

    // ---------- Потоки даних (Flow) для моніторингу в реальному часі ----------

    /**
     * «Холодний» потік телеметрії: починає опитувати сервер лише після collect
     * і кожні 2 с видає новий показник. Помилка запиту завершує потік винятком —
     * її обробляють оператори retryWhen і catch у ViewModel.
     */
    fun telemetry(): Flow<GridStatus> = flow {
        while (true) {
            emit(remote.fetchStatus().toGridStatus())
            delay(TELEMETRY_PERIOD_MS)
        }
    }

    /**
     * Потік прогнозної моделі: кожні 5 с розраховує очікуване навантаження на поточну хвилину
     * з урахуванням останньої відомої температури повітря.
     */
    fun forecastFlow(): Flow<Double> = flow {
        while (true) {
            val now = LocalTime.now()
            val thisHour = LoadForecast.forecast(now.hour, lastTemperature)
            val nextHour = LoadForecast.forecast((now.hour + 1) % 24, lastTemperature)
            emit(thisHour + (nextHour - thisHour) * now.minute / 60.0) // плавний перехід між годинами
            delay(FORECAST_PERIOD_MS)
        }
    }

    // ---------- Прогноз навантаження за погодою (Open-Meteo) ----------

    // Остання температура з Open-Meteo — використовується потоком прогнозу
    @Volatile
    private var lastTemperature = 0.0

    suspend fun getLoadForecast(): Result<LoadForecastResult> =
        remote.getWeather(KYIV_LAT, KYIV_LON).map { buildForecast(it) }
            .onSuccess { lastTemperature = it.temperature }

    private fun buildForecast(weather: WeatherResponse): LoadForecastResult {
        val currentTime = weather.current?.time.orEmpty()       // напр. 2026-11-24T00:15
        val hourly = weather.hourly
        val hours: List<HourForecast> = if (hourly == null) emptyList() else hourly.time.indices
            .filter { hourly.time[it] >= currentTime.take(13) }   // години, починаючи з поточної
            .take(12)
            .map { i ->
                val time = hourly.time[i].substringAfter("T")       // 01:00
                val temperature = hourly.temperature[i]
                val load = LoadForecast.forecast(time.take(2).toInt(), temperature)
                HourForecast(time, temperature, load, load / LoadForecast.GRID_CAPACITY_KW * 100)
            }
        return LoadForecastResult(
            temperature = weather.current?.temperature ?: 0.0,
            windSpeed = weather.current?.windSpeed ?: 0.0,
            hours = hours,
            overloadHours = hours.filter { it.percent > 100 }.map { it.time }
        )
    }

    // ---------- Адміністрування ----------

    suspend fun runDiagnostic(kind: String): Result<Unit> = remote.diagnose(kind)

    // ---------- Вимірювання (лише локальна база) ----------

    fun measurements(query: String, sortByPower: Boolean): Flow<List<MeasurementWithObject>> =
        local.searchMeasurements(query, sortByPower)

    suspend fun getMeasurement(id: Int): MeasurementEntity? = local.getMeasurement(id)

    suspend fun saveMeasurement(
        id: Int, objectId: Int, voltage: Double, current: Double, cosPhi: Double, resistance: Double
    ) {
        val entity = MeasurementEntity(
            id = id,
            objectId = objectId,
            voltage = voltage,
            current = current,
            cosPhi = cosPhi,
            resistance = resistance,
            power = Calculator.calculatePower(voltage, current, cosPhi),
            losses = Calculator.calculateLosses(current, resistance),
            createdAt = System.currentTimeMillis()
        )
        if (id == 0) local.insertMeasurement(entity) else local.updateMeasurement(entity)
    }

    suspend fun deleteMeasurement(measurement: MeasurementEntity) = local.deleteMeasurement(measurement)

    fun summarize(list: List<MeasurementWithObject>): MeasurementSummary {
        if (list.isEmpty()) return MeasurementSummary()
        val powers = list.map { it.measurement.power }
        val overloads = list.count {
            Calculator.calculateLoadPercent(it.measurement.power, it.capacityKw) > 100
        }
        return MeasurementSummary(list.size, powers.average(), powers.max(), overloads)
    }

    // ---------- Перетворення DTO → моделі застосунку ----------

    private fun GridObjectDto.toEntity(syncedAt: Long) = GridObjectEntity(
        id = id,
        name = name,
        type = type,
        capacityKw = capacityKw,
        district = location.district,
        lat = location.lat,
        lon = location.lon,
        description = description,
        status = state.status,
        loadKw = feeders.sumOf { it.loadKw }, // навантаження об'єкта = сума навантажень фідерів
        feeders = feeders.joinToString("; ") { "${it.name}: ${"%.0f".format(it.loadKw)} кВт" },
        updatedAt = state.updatedAt,
        syncedAt = syncedAt
    )

    private fun GridStatusDto.toGridStatus(): GridStatus {
        val percent = Calculator.calculateLoadPercent(loadKw, capacityKw)
        return GridStatus(mode, loadKw, frequency, percent, Calculator.getLoadStatus(percent), timestamp)
    }

    companion object {
        const val CACHE_TTL = 5 * 60 * 1000L // кеш довідника дійсний 5 хвилин
        private const val TELEMETRY_PERIOD_MS = 2000L
        private const val FORECAST_PERIOD_MS = 5000L
        private const val KYIV_LAT = 50.45
        private const val KYIV_LON = 30.52
    }
}
