package ua.kpi.smartgrid.data.remote

import com.google.gson.annotations.SerializedName

// Моделі JSON (DTO). Gson перетворює JSON-відповідь у ці класи і навпаки.

// ---------- Автентифікація ----------

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(val name: String, val email: String, val password: String)

data class LoginResponse(val token: String, val user: UserDto)

data class UserDto(val name: String, val email: String, val role: String)

data class MessageResponse(val message: String)

data class ErrorResponse(val error: String)

// ---------- Енергооб'єкти: JSON із вкладеними об'єктами та масивом ----------
// {
//   "id": 1, "name": "ПС «Північна»", "type": "SUBSTATION", "capacityKw": 1000.0,
//   "location": { "lat": 50.505, "lon": 30.498, "district": "Оболонський" },
//   "state": { "status": "ONLINE", "updatedAt": 1764000000000 },
//   "feeders": [ { "name": "Ф-1", "loadKw": 210.5 }, ... ]
// }

data class GridObjectDto(
    val id: Int,
    val name: String,
    val type: String,
    val capacityKw: Double,
    val description: String,
    val location: LocationDto,
    val state: StateDto,
    val feeders: List<FeederDto>
)

data class LocationDto(val lat: Double, val lon: Double, val district: String)

data class StateDto(val status: String, val updatedAt: Long)

data class FeederDto(val name: String, val loadKw: Double)

// ---------- Стан мережі ----------

data class GridStatusDto(
    val mode: String,
    val loadKw: Double,
    val frequency: Double,
    val capacityKw: Double,
    val timestamp: Long
) {
    // Перевірка отриманих даних: частота 45–55 Гц, навантаження не від'ємне
    fun isValid(): Boolean = frequency in 45.0..55.0 && loadKw >= 0
}

data class ModeRequest(val mode: String)

// ---------- Погода (зовнішній сервіс Open-Meteo) ----------

data class WeatherResponse(
    val current: CurrentWeatherDto?,
    val hourly: HourlyWeatherDto?
)

data class CurrentWeatherDto(
    val time: String,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("wind_speed_10m") val windSpeed: Double
)

data class HourlyWeatherDto(
    val time: List<String>,
    @SerializedName("temperature_2m") val temperature: List<Double>
)
