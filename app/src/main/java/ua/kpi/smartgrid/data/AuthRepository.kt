package ua.kpi.smartgrid.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.kpi.smartgrid.data.auth.JwtUtils
import ua.kpi.smartgrid.data.auth.Session
import ua.kpi.smartgrid.data.local.TokenStore
import ua.kpi.smartgrid.data.remote.RemoteDataSource
import ua.kpi.smartgrid.data.remote.UserDto
import ua.kpi.smartgrid.network.AppException

/**
 * Репозиторій автентифікації: вхід через API, збереження токена, відновлення сесії.
 * ViewModel не знає, де лежить токен — вона отримує готову сесію (ім'я, роль, термін дії).
 */
class AuthRepository(
    private val remote: RemoteDataSource,
    private val tokenStore: TokenStore
) {
    // Поточна сесія — розшифрований payload збереженого токена
    val session: Flow<Session?> = tokenStore.token.map { token -> token?.let { JwtUtils.decode(it) } }

    val sessionExpired: StateFlow<Boolean> = tokenStore.sessionExpired

    // Під час запуску перевіряємо збережений токен і його термін дії
    suspend fun restoreSession(): Session? {
        val token = tokenStore.token.first() ?: return null
        val session = JwtUtils.decode(token)
        if (session == null || session.isExpired()) {
            tokenStore.clear()
            tokenStore.sessionExpired.value = session != null // токен був, але прострочений
            return null
        }
        return session
    }

    suspend fun login(email: String, password: String): Result<Session> {
        val response = remote.login(email.trim().lowercase(), password).getOrElse { return Result.failure(it) }
        val session = JwtUtils.decode(response.token)
            ?: return Result.failure(AppException("Сервер повернув недійсний токен"))
        tokenStore.save(response.token) // токен зберігається в DataStore
        return Result.success(session)
    }

    suspend fun register(name: String, email: String, password: String): Result<String> =
        remote.register(name.trim(), email.trim().lowercase(), password).map { it.message }

    suspend fun logout() = tokenStore.clear()

    suspend fun getUsers(): Result<List<UserDto>> = remote.getUsers()
}
