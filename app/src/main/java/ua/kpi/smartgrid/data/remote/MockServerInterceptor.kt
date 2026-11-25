package ua.kpi.smartgrid.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import ua.kpi.smartgrid.data.auth.JwtUtils
import ua.kpi.smartgrid.data.auth.TokenPayload
import ua.kpi.smartgrid.data.local.UserDao
import ua.kpi.smartgrid.data.local.UserEntity
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalTime
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// Тестові облікові записи (показуються на екрані входу)
const val DEMO_OPERATOR_EMAIL = "operator@grid.ua"
const val DEMO_OPERATOR_PASSWORD = "grid2026"
const val DEMO_ADMIN_EMAIL = "admin@grid.ua"
const val DEMO_ADMIN_PASSWORD = "admin2026"

/**
 * Тестовий (mocked) сервер диспетчерського центру.
 *
 * Перехоплювач стоїть останнім у ланцюжку OkHttp і не відправляє запит у мережу,
 * а сам формує HTTP-відповідь із JSON. Для застосунку це звичайний REST-сервер:
 * Retrofit будує запит, Gson розбирає відповідь, коди помилок приходять як справжні.
 * Сервер видає JWT при вході, перевіряє підпис і термін дії токена та роль користувача.
 */
class MockServerInterceptor(private val userDao: UserDao) : Interceptor {

    private val gson = Gson()

    @Volatile
    private var currentMode = defaultMode() // режим мережі, який зберігає сервер

    @Volatile
    private var demoUsersReady = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        Thread.sleep(Random.nextLong(300, 800)) // мережева затримка
        return try {
            ensureDemoUsers()
            route(request)
        } catch (e: IOException) {
            throw e // тайм-аут передаємо клієнту як мережеву помилку
        } catch (e: Exception) {
            respond(request, 500, errorJson("Внутрішня помилка сервера"))
        }
    }

    private fun route(request: Request): Response {
        val method = request.method
        val path = request.url.encodedPath

        // Відкриті ресурси
        if (method == "POST" && path == "/api/auth/login") return login(request)
        if (method == "POST" && path == "/api/auth/register") return register(request)

        // Усі інші ресурси захищені — перевіряємо токен
        val header = request.header("Authorization")
        if (header.isNullOrBlank()) return respond(request, 401, errorJson("Потрібна авторизація: токен відсутній"))
        val payload = verifyToken(header.removePrefix("Bearer ").trim())
            ?: return respond(request, 401, errorJson("Недійсний токен"))
        if (payload.exp * 1000 < System.currentTimeMillis()) {
            return respond(request, 401, errorJson("Термін дії токена минув"))
        }
        val isAdmin = payload.role == "ADMIN"

        // Імітація тимчасового перевантаження сервера: довідник — кожен 6-й запит, телеметрія — кожен 10-й
        val failChance = when (path) {
            "/api/objects" -> 6
            "/api/grid/status" -> 10
            else -> 0
        }
        if (method == "GET" && failChance > 0 && Random.nextInt(failChance) == 0) {
            return respond(request, 503, errorJson("Сервер тимчасово перевантажений"))
        }

        return when {
            method == "GET" && path == "/api/objects" -> respond(request, 200, gson.toJson(objects()))

            method == "GET" && path == "/api/grid/status" -> respond(request, 200, gson.toJson(status()))

            method == "PUT" && path == "/api/grid/mode" -> {
                // Авторизація: змінювати режим може лише адміністратор
                if (!isAdmin) return respond(request, 403, errorJson("Перемикання режимів дозволено лише адміністратору"))
                val mode = gson.fromJson(readBody(request), ModeRequest::class.java)?.mode
                if (mode == null || mode !in MODE_LOAD.keys) return respond(request, 400, errorJson("Невідомий режим"))
                currentMode = mode
                respond(request, 200, gson.toJson(status()))
            }

            method == "GET" && path == "/api/admin/users" -> {
                if (!isAdmin) return respond(request, 403, errorJson("Ресурс доступний лише адміністратору"))
                val users = runBlocking { userDao.getAll() }.map { UserDto(it.name, it.email, it.role) }
                respond(request, 200, gson.toJson(users))
            }

            path.startsWith("/api/diagnostics/") -> diagnostics(request, path.substringAfterLast("/"))

            else -> respond(request, 404, errorJson("Ресурс $path не знайдено"))
        }
    }

    // ---------- Автентифікація ----------

    private fun login(request: Request): Response {
        val body = gson.fromJson(readBody(request), LoginRequest::class.java)
        val email = body?.email?.trim()?.lowercase().orEmpty()
        val password = body?.password.orEmpty()
        if (email.isBlank() || password.isBlank()) {
            return respond(request, 400, errorJson("Логін і пароль обов'язкові"))
        }
        val user = runBlocking { userDao.findByEmail(email) }
        if (user == null || user.password != password) {
            return respond(request, 401, errorJson("Невірна пошта або пароль"))
        }
        val response = LoginResponse(createToken(user), UserDto(user.name, user.email, user.role))
        return respond(request, 200, gson.toJson(response))
    }

    private fun register(request: Request): Response {
        val body = gson.fromJson(readBody(request), RegisterRequest::class.java)
            ?: return respond(request, 400, errorJson("Порожній запит"))
        val email = body.email.trim().lowercase()
        if (runBlocking { userDao.findByEmail(email) } != null) {
            return respond(request, 409, errorJson("Користувач з такою поштою вже існує"))
        }
        runBlocking { userDao.insert(UserEntity(name = body.name.trim(), email = email, password = body.password)) }
        return respond(request, 201, gson.toJson(MessageResponse("Обліковий запис створено")))
    }

    // JWT: header.payload.signature, підпис — HMAC-SHA256 із секретним ключем сервера
    private fun createToken(user: UserEntity): String {
        val header = JwtUtils.encode("""{"alg":"HS256","typ":"JWT"}""")
        val exp = System.currentTimeMillis() / 1000 + TOKEN_LIFETIME_SEC
        val payload = JwtUtils.encode(gson.toJson(TokenPayload(user.email, user.name, user.role, exp)))
        return "$header.$payload.${sign("$header.$payload")}"
    }

    private fun verifyToken(token: String): TokenPayload? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        if (sign("${parts[0]}.${parts[1]}") != parts[2]) return null // підпис не збігається — токен підроблено
        return JwtUtils.readPayload(token)
    }

    private fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.toByteArray()))
    }

    @Synchronized
    private fun ensureDemoUsers() {
        if (demoUsersReady) return
        runBlocking {
            if (userDao.findByEmail(DEMO_OPERATOR_EMAIL) == null) {
                userDao.insert(UserEntity(name = "Черговий оператор", email = DEMO_OPERATOR_EMAIL,
                    password = DEMO_OPERATOR_PASSWORD, role = "OPERATOR"))
            }
            if (userDao.findByEmail(DEMO_ADMIN_EMAIL) == null) {
                userDao.insert(UserEntity(name = "Головний диспетчер", email = DEMO_ADMIN_EMAIL,
                    password = DEMO_ADMIN_PASSWORD, role = "ADMIN"))
            }
        }
        demoUsersReady = true
    }

    // ---------- Дані мережі ----------

    private fun status(): GridStatusDto {
        val load = (MODE_LOAD[currentMode] ?: 5200.0) * Random.nextDouble(0.95, 1.05)
        val frequency = when {
            Random.nextInt(10) == 0 -> 0.0 // збій датчика частоти — клієнт має відкинути такі дані
            currentMode == "Аварія" -> 49.5 + Random.nextDouble(0.3)
            else -> 49.85 + Random.nextDouble(0.3)
        }
        return GridStatusDto(currentMode, load, frequency, GRID_CAPACITY_KW, System.currentTimeMillis())
    }

    private fun objects(): List<GridObjectDto> {
        val now = System.currentTimeMillis()
        return SEEDS.map { s ->
            val level = when (s.type) {
                "SOLAR" -> solarLevel()                       // генерація залежить від часу доби
                "STORAGE" -> Random.nextDouble(0.1, 0.6)      // віддача накопичувача
                else -> Random.nextDouble(0.45, 1.12)         // іноді понад 100 % — перевантаження
            }
            val total = if (s.status == "ONLINE") s.capacity * level else 0.0
            val feeders = (1..s.feeders).map { i ->
                FeederDto("Ф-$i", total / s.feeders * Random.nextDouble(0.85, 1.15))
            }
            GridObjectDto(
                id = s.id, name = s.name, type = s.type, capacityKw = s.capacity, description = s.description,
                location = LocationDto(s.lat, s.lon, s.district),
                state = StateDto(s.status, now - Random.nextLong(0, 10 * 60_000)),
                feeders = feeders
            )
        }
    }

    private fun solarLevel(): Double {
        val hour = LocalTime.now().hour
        return if (hour in 7..18) sin(PI * (hour - 6) / 13) * Random.nextDouble(0.6, 0.9) else 0.0
    }

    private fun diagnostics(request: Request, code: String): Response = when (code) {
        "404" -> respond(request, 404, errorJson("Ресурс не знайдено"))
        "500" -> respond(request, 500, errorJson("Внутрішня помилка сервера"))
        "timeout" -> {
            Thread.sleep(1500)
            throw SocketTimeoutException("Сервер не відповів вчасно")
        }
        else -> respond(request, 400, errorJson("Невідомий код перевірки"))
    }

    // ---------- Допоміжні функції ----------

    private fun readBody(request: Request): String {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun errorJson(message: String): String = gson.toJson(ErrorResponse(message))

    private fun respond(request: Request, code: Int, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()

    private fun defaultMode(): String = when (LocalTime.now().hour) {
        in 0..6, 23 -> "Нічний"
        in 18..22 -> "Піковий"
        else -> "Денний"
    }

    private data class ObjectSeed(
        val id: Int, val name: String, val type: String, val capacity: Double, val district: String,
        val lat: Double, val lon: Double, val feeders: Int, val description: String, val status: String = "ONLINE"
    )

    companion object {
        private const val SECRET = "smartgrid-kpi-secret"   // ключ підпису відомий лише серверу
        private const val TOKEN_LIFETIME_SEC = 10 * 60L     // токен дійсний 10 хвилин
        private const val GRID_CAPACITY_KW = 10000.0

        // Навантаження мережі в кожному режимі, кВт (значення з практичної роботи №1)
        private val MODE_LOAD = mapOf("Денний" to 5200.0, "Нічний" to 2100.0, "Піковий" to 8700.0, "Аварія" to 10500.0)

        private val SEEDS = listOf(
            ObjectSeed(1, "ПС «Північна»", "SUBSTATION", 1000.0, "Оболонський", 50.5060, 30.4985, 4,
                "Підстанція 110/10 кВ, живить житловий масив Оболоні"),
            ObjectSeed(2, "ПС «Центральна»", "SUBSTATION", 2500.0, "Шевченківський", 50.4480, 30.5150, 6,
                "Підстанція 110/10 кВ центральної частини міста"),
            ObjectSeed(3, "ПС «Південна»", "SUBSTATION", 630.0, "Голосіївський", 50.3960, 30.5080, 4,
                "Підстанція 35/10 кВ промислової зони"),
            ObjectSeed(4, "ТП-115 «Лук'янівка»", "TRANSFORMER", 400.0, "Шевченківський", 50.4620, 30.4810, 2,
                "Трансформаторна підстанція 10/0,4 кВ житлового кварталу"),
            ObjectSeed(5, "ТП-208 «Позняки»", "TRANSFORMER", 630.0, "Дарницький", 50.3990, 30.6330, 3,
                "ТП 10/0,4 кВ, багатоповерхова забудова"),
            ObjectSeed(6, "ТП-312 «Виноградар»", "TRANSFORMER", 400.0, "Подільський", 50.5100, 30.4300, 2,
                "ТП 10/0,4 кВ, планова заміна трансформатора", status = "MAINTENANCE"),
            ObjectSeed(7, "СЕС «Троєщина»", "SOLAR", 750.0, "Деснянський", 50.5150, 30.6050, 2,
                "Дахова сонячна електростанція торгового центру"),
            ObjectSeed(8, "СЕС «Осокорки»", "SOLAR", 500.0, "Дарницький", 50.3930, 30.6150, 2,
                "Наземна сонячна електростанція"),
            ObjectSeed(9, "СНЕ «Лівобережна»", "STORAGE", 1000.0, "Дніпровський", 50.4520, 30.5980, 1,
                "Система накопичення енергії, згладжує пікові навантаження"),
            ObjectSeed(10, "ПС «Святошинська»", "SUBSTATION", 1600.0, "Святошинський", 50.4580, 30.3920, 5,
                "Підстанція 110/10 кВ західної частини міста"),
            ObjectSeed(11, "ТП-421 «Теремки»", "TRANSFORMER", 630.0, "Голосіївський", 50.3680, 30.4570, 3,
                "ТП 10/0,4 кВ, житловий масив Теремки"),
            ObjectSeed(12, "ТП-507 «Лісовий»", "TRANSFORMER", 400.0, "Деснянський", 50.4650, 30.6450, 2,
                "ТП 10/0,4 кВ, аварійне відключення фідера", status = "OFFLINE")
        )
    }
}
