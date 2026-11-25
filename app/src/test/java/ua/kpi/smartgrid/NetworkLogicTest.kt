package ua.kpi.smartgrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kpi.smartgrid.data.auth.JwtUtils
import ua.kpi.smartgrid.domain.LoadForecast

/** Перевірка розбору токена та моделі прогнозу. */
class NetworkLogicTest {

    @Test
    fun token_payloadIsDecoded() {
        val header = JwtUtils.encode("""{"alg":"HS256","typ":"JWT"}""")
        val payload = JwtUtils.encode("""{"sub":"admin@grid.ua","name":"Admin","role":"ADMIN","exp":4102444800}""")
        val session = JwtUtils.decode("$header.$payload.signature")
        assertEquals("ADMIN", session?.role)
        assertTrue(session?.isAdmin == true)
    }

    @Test
    fun token_brokenIsRejected() {
        assertNull(JwtUtils.decode("not-a-token"))
    }

    @Test
    fun forecast_coldWeatherIncreasesLoad() {
        val warm = LoadForecast.forecast(hour = 12, temperature = 20.0)
        val cold = LoadForecast.forecast(hour = 12, temperature = -10.0)
        assertTrue(cold > warm)
        assertEquals(1.0, LoadForecast.temperatureFactor(20.0), 0.001)
    }
}
