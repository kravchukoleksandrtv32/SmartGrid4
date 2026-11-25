package ua.kpi.smartgrid.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST API сервера диспетчерського центру. Retrofit сам створює реалізацію інтерфейсу:
 * анотація задає HTTP-метод і шлях, а Gson перетворює тіло запиту й відповіді.
 */
interface GridApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): MessageResponse

    // Довідник енергооб'єктів (вкладені location, state і масив feeders)
    @GET("api/objects")
    suspend fun getObjects(): List<GridObjectDto>

    // Поточний стан мережі
    @GET("api/grid/status")
    suspend fun getStatus(): GridStatusDto

    // Зміна режиму — лише для адміністратора
    @PUT("api/grid/mode")
    suspend fun setMode(@Body body: ModeRequest): GridStatusDto

    // Захищений ресурс адміністратора
    @GET("api/admin/users")
    suspend fun getUsers(): List<UserDto>

    // Той самий ресурс із явно заданим заголовком — для перевірки доступу без токена або з недійсним токеном
    @GET("api/admin/users")
    suspend fun getUsersWithHeader(@Header("Authorization") authorization: String): List<UserDto>

    // Діагностичний ресурс: сервер повертає заданий код помилки
    @GET("api/diagnostics/{code}")
    suspend fun diagnostics(@Path("code") code: String): MessageResponse
}

/** Зовнішній сервіс прогнозу погоди Open-Meteo (без ключа API). */
interface WeatherApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,wind_speed_10m",
        @Query("hourly") hourly: String = "temperature_2m",
        @Query("forecast_days") days: Int = 2,
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}
