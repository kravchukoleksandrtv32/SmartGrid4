package ua.kpi.smartgrid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.kpi.smartgrid.data.GridRepository
import ua.kpi.smartgrid.data.GridStatus

sealed class MonitoringUiState {
    object Loading : MonitoringUiState()
    data class Success(val status: GridStatus) : MonitoringUiState()
    data class Error(val message: String) : MonitoringUiState()
}

/** Режими мережі: разовий запит стану і зміна режиму (лише адміністратор). */
class ModesViewModel(private val repository: GridRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MonitoringUiState>(MonitoringUiState.Loading)
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = MonitoringUiState.Loading
            repository.getGridStatus()
                .onSuccess { _uiState.value = MonitoringUiState.Success(it) }
                .onFailure {
                    _uiState.value = MonitoringUiState.Error(it.message.orEmpty())
                    _message.value = it.message
                }
        }
    }

    // Сервер дозволяє зміну режиму лише адміністратору, інакше — 403
    fun setMode(mode: String) {
        viewModelScope.launch {
            repository.setMode(mode)
                .onSuccess {
                    _uiState.value = MonitoringUiState.Success(it)
                    _message.value = "Режим змінено: $mode"
                }
                .onFailure { _message.value = it.message }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
