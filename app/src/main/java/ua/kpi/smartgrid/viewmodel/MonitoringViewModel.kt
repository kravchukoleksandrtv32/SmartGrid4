package ua.kpi.smartgrid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.smartgrid.data.GridRepository
import ua.kpi.smartgrid.data.GridStatus
import ua.kpi.smartgrid.data.LoadForecastResult
import ua.kpi.smartgrid.network.AppException

/** Точка графіка: фактичне і прогнозне навантаження. */
data class LivePoint(val loadKw: Double, val forecastKw: Double)

/** Стан моніторингу в реальному часі. */
data class LiveState(
    val status: GridStatus? = null,         // останній коректний показник
    val forecastKw: Double = 0.0,           // прогноз моделі
    val deviationPercent: Double = 0.0,     // відхилення факту від прогнозу
    val history: List<LivePoint> = emptyList()
)

/**
 * Моніторинг мережі на Flow і корутинах.
 *
 * telemetry — «гарячий» потік показників (shareIn): один опит сервера для всіх підписників.
 * liveState — поєднання телеметрії з потоком прогнозної моделі (combine) і накопичення історії (scan).
 * Другий підписник телеметрії рахує перевантаження й показує попередження.
 */
class MonitoringViewModel(private val repository: GridRepository) : ViewModel() {

    // Прогноз навантаження на 12 год за погодою (разовий запит)
    private val _forecast = MutableStateFlow<LoadForecastResult?>(null)
    val forecast: StateFlow<LoadForecastResult?> = _forecast.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ---------- Стан потоку телеметрії ----------

    private val _reconnect = MutableStateFlow<String?>(null) // текст про повторне підключення
    val reconnect: StateFlow<String?> = _reconnect.asStateFlow()

    private val _rejected = MutableStateFlow(0)              // відкинуті збійні показники
    val rejected: StateFlow<Int> = _rejected.asStateFlow()

    private val _overloads = MutableStateFlow(0)             // кількість перевантажень за сесію
    val overloads: StateFlow<Int> = _overloads.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null) // помилка, після якої потік зупинено
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    private var failedAttempts = 0 // невдалі спроби поспіль

    /**
     * «Гарячий» потік телеметрії. SharingStarted.Eagerly — збір починається одразу
     * і триває, поки існує ViewModel, незалежно від кількості підписників.
     */
    private val telemetry: SharedFlow<GridStatus> = repository.telemetry()
        .onEach { if (!it.isValid) _rejected.update { n -> n + 1 } }
        .filter { it.isValid }                       // відкидаємо збійні показники датчика (частота 0 Гц)
        .onEach {                                    // дані надходять — зв'язок відновлено
            failedAttempts = 0
            _reconnect.value = null
        }
        .retryWhen { cause, _ ->
            // Автоматичне відновлення збору при тимчасовій помилці або відсутності мережі
            val canRetry = cause is AppException && cause.canRetry
            if (canRetry) {
                failedAttempts++
                _reconnect.value = "${cause.message} Повторне підключення, спроба $failedAttempts…"
                delay(minOf(2000L * failedAttempts, 10_000L)) // затримка зростає до 10 с
            }
            canRetry
        }
        .catch { _streamError.value = it.message }   // постійна помилка (напр. 401) — зупиняємо потік
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    /** Факт + прогноз: combine видає нове значення, коли змінюється будь-який з потоків. */
    val liveState: StateFlow<LiveState> =
        combine(telemetry, repository.forecastFlow()) { status, forecastKw -> status to forecastKw }
            .scan(LiveState()) { state, (status, forecastKw) ->
                state.copy(
                    status = status,
                    forecastKw = forecastKw,
                    deviationPercent = (status.loadKw - forecastKw) / forecastKw * 100,
                    history = (state.history + LivePoint(status.loadKw, forecastKw)).takeLast(MAX_POINTS)
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveState())

    init {
        // Другий підписник того самого потоку: реагуємо лише на момент переходу в перевантаження
        telemetry
            .map { it.percent > 100 }
            .distinctUntilChanged()
            .filter { overloaded -> overloaded }
            .onEach {
                _overloads.update { n -> n + 1 }
                _message.value = "Увага: навантаження перевищило допустиме!"
            }
            .launchIn(viewModelScope)

        loadForecast() // температура для прогнозної моделі
    }

    fun loadForecast() {
        viewModelScope.launch {
            repository.getLoadForecast()
                .onSuccess { _forecast.value = it }
                .onFailure { _message.value = "Прогноз погоди: ${it.message}" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        const val MAX_POINTS = 30 // точок на графіку
    }
}
