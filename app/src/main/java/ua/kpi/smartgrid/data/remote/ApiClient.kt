package ua.kpi.smartgrid.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ua.kpi.smartgrid.data.auth.AuthInterceptor
import ua.kpi.smartgrid.data.local.TokenStore
import java.util.concurrent.TimeUnit

/**
 * Створення клієнтів Retrofit.
 * Ланцюжок OkHttp для сервера мережі: AuthInterceptor (токен) → журнал запитів → MockServerInterceptor.
 */
object ApiClient {

    // Адреса сервера диспетчерського центру (відповіді формує MockServerInterceptor)
    private const val GRID_BASE_URL = "https://api.smartgrid.local/"

    // Зовнішній сервіс погоди
    private const val WEATHER_BASE_URL = "https://api.open-meteo.com/"

    // Журнал HTTP-запитів у Logcat (тег okhttp.OkHttpClient); токен у журнал не виводимо
    private fun logging() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    fun createGridApi(tokenStore: TokenStore, mockServer: MockServerInterceptor): GridApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore)) // 1. додає Authorization: Bearer <token>
            .addInterceptor(logging())                   // 2. записує запит і відповідь у журнал
            .addInterceptor(mockServer)                  // 3. тестовий сервер повертає JSON
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(GRID_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create()) // JSON ⇄ Kotlin-об'єкти
            .build()
            .create(GridApi::class.java)
    }

    fun createWeatherApi(): WeatherApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(logging())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }
}
