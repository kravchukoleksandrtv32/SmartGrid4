package ua.kpi.smartgrid.network

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * Безпечний виклик API: усі мережеві запити застосунку проходять через цей клас.
 * 1) перевіряє підключення до інтернету;
 * 2) при тимчасовій помилці повторює запит із зростаючою затримкою (1 с, 2 с, …);
 * 3) остаточну помилку передає в ErrorHandler і повертає Result.failure з готовим повідомленням.
 */
class ApiCaller(
    private val network: NetworkChecker,
    private val errors: ErrorHandler
) {
    suspend fun <T> call(action: String, retries: Int = 2, request: suspend () -> T): Result<T> {
        if (!network.isOnline()) {
            val error = NoInternetException()
            return Result.failure(AppException(errors.handle(action, error), error))
        }

        var lastError: Exception? = null
        for (attempt in 0..retries) {
            if (attempt > 0) {
                lastError?.let { errors.logRetry(action, it, attempt, retries) }
                delay(1000L * attempt)
            }
            try {
                return Result.success(request())
            } catch (e: CancellationException) {
                throw e // скасування корутини (вихід з екрана) — не помилка
            } catch (e: Exception) {
                lastError = e
                if (!errors.isTemporary(e)) break // постійна помилка — повтор не допоможе
            }
        }

        val error = lastError ?: Exception("Невідома помилка")
        return Result.failure(AppException(errors.handle(action, error), error))
    }

    /**
     * Один запит без повторів — для потоків Flow. Помилка записується в журнал
     * і передається далі як AppException; повторний збір виконує оператор retryWhen у ViewModel.
     */
    suspend fun <T> callOrThrow(action: String, request: suspend () -> T): T {
        try {
            if (!network.isOnline()) throw NoInternetException()
            return request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Для безперервного потоку повторюємо і тимчасові помилки, і відсутність мережі
            val canRetry = errors.isTemporary(e) || e is NoInternetException
            throw AppException(errors.handle(action, e), e, canRetry)
        }
    }
}
