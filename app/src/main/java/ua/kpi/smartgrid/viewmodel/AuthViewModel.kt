package ua.kpi.smartgrid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.kpi.smartgrid.data.AuthRepository
import ua.kpi.smartgrid.data.auth.Session

data class AuthUiState(
    val isLoading: Boolean = false,  // показувати індикатор завантаження
    val error: String? = null,       // текст помилки
    val message: String? = null      // повідомлення про успіх
)

/**
 * Стан автентифікації. Сесія (ім'я, роль, термін дії) береться з токена,
 * тому після перезапуску застосунку користувач залишається в системі, доки токен дійсний.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Поточна сесія; інтерфейс показує або приховує елементи залежно від ролі
    val session: StateFlow<Session?> =
        repository.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // true — сервер відхилив прострочений токен, потрібен повторний вхід
    val sessionExpired: StateFlow<Boolean> = repository.sessionExpired

    suspend fun restoreSession(): Session? = repository.restoreSession()

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repository.login(email, password)
                .onSuccess {
                    _uiState.value = AuthUiState()
                    onSuccess()
                }
                .onFailure { _uiState.value = AuthUiState(error = it.message) }
        }
    }

    fun register(name: String, email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repository.register(name, email, password)
                .onSuccess {
                    _uiState.value = AuthUiState(message = "Реєстрацію завершено, увійдіть")
                    onSuccess()
                }
                .onFailure { _uiState.value = AuthUiState(error = it.message) }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onDone()
        }
    }

    fun clearMessages() {
        _uiState.value = AuthUiState()
    }
}
