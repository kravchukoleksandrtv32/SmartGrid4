package ua.kpi.smartgrid.data.auth

import okhttp3.Interceptor
import okhttp3.Response
import ua.kpi.smartgrid.data.local.TokenStore

/**
 * Interceptor автоматично додає токен у заголовок кожного запиту:
 * Authorization: Bearer <token>. Завдяки цьому методи Retrofit не передають токен вручну.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Вхід і реєстрація не потребують токена.
        // Якщо заголовок уже задано вручну (перевірка доступу в адмін-панелі) — не змінюємо його.
        if (request.url.encodedPath.startsWith("/api/auth/") || request.header("Authorization") != null) {
            return chain.proceed(request)
        }

        val token = tokenStore.currentToken() ?: return chain.proceed(request)
        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )

        // Сервер відхилив токен (прострочений або недійсний) — завершуємо сесію,
        // навігація відкриє екран входу з повідомленням
        if (response.code == 401) tokenStore.expire()
        return response
    }
}
