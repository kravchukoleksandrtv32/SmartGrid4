package ua.kpi.smartgrid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.smartgrid.data.GridRepository
import ua.kpi.smartgrid.data.ObjectsSummary
import ua.kpi.smartgrid.data.local.GridObjectEntity
import ua.kpi.smartgrid.data.local.MeasurementEntity
import ua.kpi.smartgrid.domain.ObjectFilter
import ua.kpi.smartgrid.domain.ObjectFilters
import ua.kpi.smartgrid.domain.ObjectItem
import ua.kpi.smartgrid.domain.SortOption
import ua.kpi.smartgrid.domain.UserLocation

/**
 * Енергооб'єкти: спільна ViewModel для списку, екрана деталей і мапи.
 * Тут зберігаються пошук, фільтри, сортування, вибраний об'єкт і позиція користувача,
 * тому вибір у списку одразу видно на мапі, і навпаки.
 */
@OptIn(FlowPreview::class)
class ObjectsViewModel(private val repository: GridRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val filters = MutableStateFlow(ObjectFilters())

    private val _selectedId = MutableStateFlow<Int?>(null)
    val selectedId: StateFlow<Int?> = _selectedId.asStateFlow()

    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Список для екрана. combine перераховує його, коли змінюються дані в базі,
     * текст пошуку (debounce — через 300 мс після останнього символу), фільтри або позиція GPS.
     */
    val items: StateFlow<List<ObjectItem>> =
        combine(repository.objects, query.debounce(300), filters, _userLocation) { list, q, f, location ->
            ObjectFilter.apply(list, q, f, location)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Загальна кількість об'єктів (для підпису «знайдено N з M»)
    val totalCount: StateFlow<Int> =
        repository.objects.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val summary: StateFlow<ObjectsSummary> =
        repository.objects.map { repository.summarizeObjects(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ObjectsSummary())

    // Дані одного об'єкта для екрана деталей
    fun objectById(id: Int): Flow<GridObjectEntity?> = repository.objectById(id)

    fun measurementsFor(id: Int): Flow<List<MeasurementEntity>> = repository.measurementsForObject(id)

    // ---------- Оновлення довідника ----------

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.refreshObjects(force)
                .onSuccess { fromServer ->
                    _isOffline.value = false
                    if (fromServer) _message.value = "Дані оновлено з сервера"
                }
                .onFailure {
                    _isOffline.value = true
                    _message.value = it.message
                }
            _lastSync.value = repository.lastSyncTime()
            _isLoading.value = false
        }
    }

    // ---------- Фільтри та сортування ----------

    // Тип можна обрати кілька разів: повторне натискання знімає вибір
    fun toggleType(type: String) = filters.update { f ->
        f.copy(types = if (type in f.types) f.types - type else f.types + type)
    }

    fun setState(state: String?) = filters.update { it.copy(state = state) }

    fun setMinCapacity(value: Float) = filters.update { it.copy(minCapacity = value) }

    // Повторне натискання на той самий критерій змінює напрямок сортування
    fun setSort(option: SortOption) = filters.update { f ->
        if (f.sort == option) f.copy(ascending = !f.ascending) else f.copy(sort = option, ascending = true)
    }

    fun resetFilters() {
        query.value = ""
        filters.value = ObjectFilters()
    }

    // ---------- Вибір об'єкта і геолокація ----------

    fun select(id: Int?) {
        _selectedId.value = id
    }

    // Натискання на вже вибрану картку знімає виділення
    fun toggleSelection(id: Int) {
        _selectedId.value = if (_selectedId.value == id) null else id
    }

    fun updateLocation(lat: Double, lon: Double) {
        _userLocation.value = UserLocation(lat, lon)
    }

    fun clearMessage() {
        _message.value = null
    }
}
