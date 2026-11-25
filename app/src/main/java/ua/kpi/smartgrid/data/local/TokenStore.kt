package ua.kpi.smartgrid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Файл DataStore для даних авторизації
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/**
 * Локальне сховище токена (DataStore Preferences).
 * Токен зберігається між запусками, тож після перезапуску вхід не потрібен, доки токен дійсний.
 */
class TokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")

    // Потік з поточним токеном (null — користувач не увійшов)
    val token: Flow<String?> = context.authDataStore.data.map { it[tokenKey] }

    // true — сесію завершено, бо термін дії токена минув (показуємо повідомлення на екрані входу)
    val sessionExpired = MutableStateFlow(false)

    suspend fun save(token: String) {
        context.authDataStore.edit { it[tokenKey] = token }
        sessionExpired.value = false
    }

    suspend fun clear() {
        context.authDataStore.edit { it.remove(tokenKey) }
    }

    // Синхронне читання для Interceptor (він працює у фоновому потоці OkHttp)
    fun currentToken(): String? = runBlocking { token.first() }

    // Викликається, коли сервер відповів 401 на запит із токеном
    fun expire() {
        runBlocking { clear() }
        sessionExpired.value = true
    }
}
