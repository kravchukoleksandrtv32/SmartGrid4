package ua.kpi.smartgrid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.kpi.smartgrid.data.AuthRepository
import ua.kpi.smartgrid.data.GridRepository
import ua.kpi.smartgrid.data.remote.UserDto
import ua.kpi.smartgrid.network.ErrorLogger

/**
 * Панель адміністратора: список користувачів (захищений ресурс),
 * перевірка обробки помилок і перегляд журналу помилок.
 */
class AdminViewModel(
    private val authRepository: AuthRepository,
    private val gridRepository: GridRepository,
    private val errorLogger: ErrorLogger
) : ViewModel() {

    private val _users = MutableStateFlow<List<UserDto>>(emptyList())
    val users: StateFlow<List<UserDto>> = _users.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            authRepository.getUsers()
                .onSuccess { _users.value = it }
                .onFailure { _message.value = it.message }
            _isLoading.value = false
            loadLog()
        }
    }

    // kind: no_token, bad_token, 404, 500, timeout
    fun runDiagnostic(kind: String) {
        viewModelScope.launch {
            _isLoading.value = true
            gridRepository.runDiagnostic(kind)
                .onSuccess { _message.value = "Запит виконано успішно" }
                .onFailure { _message.value = it.message }
            _isLoading.value = false
            loadLog()
        }
    }

    fun loadLog() {
        viewModelScope.launch {
            _log.value = withContext(Dispatchers.IO) { errorLogger.read() } // читання файла у фоні
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { errorLogger.clear() }
            _log.value = emptyList()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
