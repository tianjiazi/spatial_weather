package com.pico.spatialweather

import com.pico.spatialweather.content.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WeatherContractTest {
    @Test fun missingValuesStayMissingAndHumidityIsAlreadyPercent() {
        val live = parseLive(JSONObject("""{"lives":[{"temperature":"","humidity":"62","windpower":"≤3","winddirection":"东南","reporttime":"2026-11-10 12:00:00"}]}"""))
        assertNull(live.temp)
        assertEquals(62.0, live.humidity!!, 0.0)
        assertEquals("—", temperature(live.temp, false))
        assertEquals("≤3级", power(live.power))
        assertEquals("东南风", direction(live.wind))
        assertEquals("无风", direction("无风"))
    }
    @Test fun forecastUsesActualDatesAndDayNightFields() {
        val result = parseForecast(JSONObject("""{"forecasts":[{"reporttime":"2026-11-10 08:00:00","casts":[
          {"date":"2026-11-12","daytemp":"8","nighttemp":"-3","dayweather":"晴","nightweather":"多云"},
          {"date":"2026-11-11","daytemp":"10","nighttemp":"","daypower":"3-4"},
          {"date":"2026-11-11","daytemp":"99"},
          {"date":"invalid","daytemp":"10"}]}]}"""))
        assertEquals(listOf("2026-11-11", "2026-11-12"), result.first.map { it.date })
        assertEquals(2, result.first.size)
        assertNull(result.first[0].nightTemp)
        assertEquals(-3.0, result.first[1].nightTemp!!, 0.0)
        assertEquals("3-4级", power(result.first[0].dayPower))
        assertEquals("2026-11-10 08:00:00", result.second)
    }
    @Test fun temperatureConversionSupportsNegativeAndMissing() {
        assertEquals("32°", temperature(0.0, true))
        assertEquals("-40°", temperature(-40.0, true))
        assertEquals("—", temperature(null, true))
    }
    @Test fun emptyResultsAreFailuresRatherThanZeroWeather() {
        assertTrue(runCatching { parseLive(JSONObject("""{"lives":[]}""")) }.isFailure)
        assertTrue(runCatching { parseForecast(JSONObject("""{"forecasts":[{"casts":[]}]}""")) }.isFailure)
    }
}
