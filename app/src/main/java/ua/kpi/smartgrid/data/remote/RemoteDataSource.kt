package ua.kpi.smartgrid.data.remote

import ua.kpi.smartgrid.network.ApiCaller
import ua.kpi.smartgrid.network.InvalidDataException

/**
 * Віддалене джерело даних (Data Source): усі звернення до REST API.
 * Кожен виклик іде через ApiCaller — перевірка мережі, повторні спроби, обробка помилок,
 * тому методи повертають Result і не кидають винятків.
 */
class RemoteDataSource(
    private val gridApi: GridApi,
    private val weatherApi: WeatherApi,
    private val caller: ApiCaller
) {
    // Вхід і реєстрацію не повторюємо: помилка 401/409 не зникне від повтору
    suspend fun login(email: String, password: String) =
        caller.call("Вхід у систему", retries = 0) { gridApi.login(LoginRequest(email, password)) }

    suspend fun register(name: String, email: String, password: String) =
        caller.call("Реєстрація", retries = 0) { gridApi.register(RegisterRequest(name, email, password)) }

    suspend fun getObjects() =
        caller.call("Завантаження енергооб'єктів") { gridApi.getObjects() }

    // Перевіряємо отримані показники; некоректні дані вважаються тимчасовою помилкою (буде повтор)
    suspend fun getStatus() =
        caller.call("Отримання стану мережі") {
            gridApi.getStatus().also { if (!it.isValid()) throw InvalidDataException() }
        }

    // Для потоку телеметрії: один запит, без перевірки даних — некоректні показники відкидає оператор filter
    suspend fun fetchStatus() =
        caller.callOrThrow("Телеметрія мережі") { gridApi.getStatus() }

    suspend fun setMode(mode: String) =
        caller.call("Зміна режиму мережі", retries = 0) { gridApi.setMode(ModeRequest(mode)) }

    suspend fun getUsers() =
        caller.call("Список користувачів") { gridApi.getUsers() }

    suspend fun getWeather(latitude: Double, longitude: Double) =
        caller.call("Прогноз погоди Open-Meteo") {
            weatherApi.getForecast(latitude, longitude).also {
                if (it.current == null || it.hourly == null) throw InvalidDataException()
            }
        }

    // Перевірка обробки помилок: доступ без токена, з недійсним токеном, різні HTTP-коди, тайм-аут
    suspend fun diagnose(kind: String) =
        caller.call("Перевірка «$kind»") {
            when (kind) {
                "no_token" -> gridApi.getUsersWithHeader("")
                "bad_token" -> gridApi.getUsersWithHeader("Bearer invalid.token.value")
                else -> gridApi.diagnostics(kind)
            }
            Unit
        }
}
