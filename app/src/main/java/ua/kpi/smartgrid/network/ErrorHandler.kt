package ua.kpi.smartgrid.network

import com.google.gson.Gson
import com.google.gson.JsonParseException
import retrofit2.HttpException
import ua.kpi.smartgrid.data.remote.ErrorResponse
import java.io.IOException
import java.net.SocketTimeoutException

/** Немає підключення до інтернету (перевіряється до запиту). */
class NoInternetException : IOException("Відсутнє з'єднання з інтернетом")

/** Сервер повернув дані, які не пройшли перевірку. */
class InvalidDataException : IOException("Некоректні дані від сервера")

/** Помилка з уже готовим повідомленням для користувача; canRetry — чи має сенс повторити запит. */
class AppException(message: String, cause: Throwable? = null, val canRetry: Boolean = false) : Exception(message, cause)

/**
 * Централізований обробник помилок. Визначає тип помилки, формує зрозуміле повідомлення,
 * записує помилку в журнал і вирішує, чи варто повторювати запит.
 * Інтерфейс отримує лише текст повідомлення і не знає деталей помилки.
 */
class ErrorHandler(private val logger: ErrorLogger) {

    private val gson = Gson()

    fun handle(action: String, error: Throwable): String {
        val message = messageFor(error)
        logger.log(action, error, message)
        return message
    }

    fun logRetry(action: String, error: Throwable, attempt: Int, maxAttempts: Int) {
        logger.log(action, error, "${messageFor(error)} — повторна спроба $attempt з $maxAttempts")
    }

    // Тимчасові помилки (мережа, тайм-аут, 5xx) можна повторити; 4xx і відсутність мережі — ні
    fun isTemporary(error: Throwable): Boolean = when (error) {
        is NoInternetException -> false
        is IOException -> true
        is HttpException -> error.code() >= 500
        else -> false
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is NoInternetException -> "Відсутнє з'єднання з інтернетом. Перевірте підключення."
        is SocketTimeoutException -> "Сервер не відповідає (тайм-аут). Спробуйте пізніше."
        is InvalidDataException -> "Отримано некоректні дані від сервера"
        is HttpException -> when (error.code()) {
            400 -> serverMessage(error) ?: "Некоректний запит (400)"
            401 -> serverMessage(error) ?: "Потрібна авторизація (401)"
            403 -> serverMessage(error) ?: "Недостатньо прав доступу (403)"
            404 -> "Ресурс не знайдено на сервері (404)"
            409 -> serverMessage(error) ?: "Конфлікт даних (409)"
            500 -> "Внутрішня помилка сервера (500). Спробуйте пізніше."
            502, 503, 504 -> "Сервер тимчасово недоступний (${error.code()}). Спробуйте пізніше."
            else -> "Помилка сервера (код ${error.code()})"
        }
        is JsonParseException -> "Не вдалося розібрати відповідь сервера"
        is IOException -> "Помилка мережі. Перевірте підключення."
        else -> error.message ?: "Невідома помилка"
    }

    // Текст помилки з тіла відповіді сервера: {"error": "..."}
    private fun serverMessage(error: HttpException): String? = try {
        val body = error.response()?.errorBody()?.string()
        gson.fromJson(body, ErrorResponse::class.java)?.error
    } catch (e: Exception) {
        null
    }
}
