package it.svegliami

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/** Client per le API gratuite di Open-Meteo (nessuna chiave richiesta). */
object WeatherClient {
    data class Place(val name: String, val lat: Double, val lon: Double)

    fun searchPlaces(query: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=it&format=json"))
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val label = listOf(r.optString("name"), r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    fun fetchForecastJson(lat: Double, lon: Double): String {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&minutely_15=precipitation&hourly=precipitation,precipitation_probability" +
                "&timezone=auto&forecast_days=2",
            lat, lon,
        )
        return httpGet(url)
    }

    fun parseForecast(json: String): Forecast {
        val root = JSONObject(json)
        val offset = ZoneOffset.ofTotalSeconds(root.optInt("utc_offset_seconds", 0))
        fun slots(block: String, field: String, lenSec: Long): List<Slot> {
            val b = root.optJSONObject(block) ?: return emptyList()
            val times = b.optJSONArray("time") ?: return emptyList()
            val values: JSONArray = b.optJSONArray(field) ?: return emptyList()
            return (0 until times.length()).map { i ->
                // Open-Meteo: il valore si riferisce all'intervallo che TERMINA all'istante indicato.
                val end = LocalDateTime.parse(times.getString(i)).toEpochSecond(offset)
                val v = if (values.isNull(i)) Double.NaN else values.getDouble(i)
                Slot(end - lenSec, end, v)
            }
        }
        return Forecast(
            precip15 = slots("minutely_15", "precipitation", 900),
            precipHourly = slots("hourly", "precipitation", 3600),
            probHourly = slots("hourly", "precipitation_probability", 3600),
        )
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "Svegliami-Android")
        try {
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
