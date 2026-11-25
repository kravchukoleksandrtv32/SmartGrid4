package ua.kpi.smartgrid.data.auth

import com.google.gson.Gson
import java.util.Base64

/** Дані з токена, з якими працює інтерфейс. */
data class Session(
    val name: String,
    val email: String,
    val role: String,     // OPERATOR або ADMIN
    val expiresAt: Long   // мс
) {
    val isAdmin: Boolean get() = role == "ADMIN"
    val roleTitle: String get() = if (isAdmin) "Адміністратор" else "Оператор"
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
}

/** Корисне навантаження (payload) JWT: sub — пошта, exp — час завершення в секундах. */
data class TokenPayload(val sub: String, val name: String, val role: String, val exp: Long)

/**
 * Робота з JWT на стороні клієнта. Токен має вигляд header.payload.signature,
 * кожна частина закодована Base64URL. Клієнт лише читає payload (ім'я, роль, термін дії),
 * а підпис перевіряє сервер, бо тільки він знає секретний ключ.
 */
object JwtUtils {
    private val gson = Gson()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(json: String): String = encoder.encodeToString(json.toByteArray())

    fun readPayload(token: String): TokenPayload? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        return try {
            gson.fromJson(String(decoder.decode(parts[1])), TokenPayload::class.java)
        } catch (e: Exception) {
            null // пошкоджений токен
        }
    }

    fun decode(token: String): Session? {
        val payload = readPayload(token) ?: return null
        return Session(payload.name, payload.sub, payload.role, payload.exp * 1000)
    }
}
