package com.pico.spatialweather.content

import android.content.Context
import android.icu.text.Transliterator
import com.pico.spatialweather.BuildConfig
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import kotlin.math.roundToInt

data class City(val code: String, val name: String, val path: String, val alias: String = "", val located: Boolean = false) {
    val id get() = if (located) "location" else code
    fun json() = JSONObject().put("code", code).put("name", name).put("path", path).put("alias", alias).put("located", located)
    companion object {
        fun parse(o: JSONObject) = City(o.optString("code"), o.optString("name"), o.optString("path"), o.optString("alias"), o.optBoolean("located"))
    }
}
data class Live(val weather: String, val temp: Double?, val humidity: Double?, val wind: String, val power: String, val published: String)
data class Day(val date: String, val dayWeather: String, val nightWeather: String, val dayTemp: Double?, val nightTemp: Double?, val dayWind: String, val nightWind: String, val dayPower: String, val nightPower: String)
data class Weather(val live: Live?, val days: List<Day>, val forecastTime: String, val fetched: Long, val cached: Boolean = false, val error: String? = null)
fun temperature(value: Double?, fahrenheit: Boolean): String = value?.let { "${(if (fahrenheit) it * 1.8 + 32 else it).roundToInt()}°" } ?: "—"
fun power(value: String) = if (value.isBlank()) "—" else if (value.endsWith("级")) value else "${value}级"
fun direction(value: String) = if (value.isBlank()) "—" else if (value.endsWith("风")) value else "${value}风"
private fun JSONObject.text(key: String) = optString(key).takeUnless { it == "null" || it == "[]" }.orEmpty()
private fun JSONObject.number(key: String) = text(key).toDoubleOrNull()?.takeIf { it.isFinite() }
fun parseLive(root: JSONObject): Live {
    val o = root.optJSONArray("lives")?.optJSONObject(0) ?: error("该地区暂无实况天气")
    return Live(o.text("weather"), o.number("temperature"), o.number("humidity")?.takeIf { it in 0.0..100.0 }, o.text("winddirection"), o.text("windpower"), o.text("reporttime"))
}
fun parseForecast(root: JSONObject): Pair<List<Day>, String> {
    val o = root.optJSONArray("forecasts")?.optJSONObject(0) ?: error("该地区暂无短期预报")
    val casts = o.optJSONArray("casts") ?: error("该地区暂无短期预报")
    val days = (0 until casts.length()).mapNotNull {
        val c = casts.optJSONObject(it) ?: return@mapNotNull null
        val date = c.text("date")
        if (runCatching { LocalDate.parse(date) }.isFailure) return@mapNotNull null
        Day(date, c.text("dayweather"), c.text("nightweather"), c.number("daytemp"), c.number("nighttemp"), c.text("daywind"), c.text("nightwind"), c.text("daypower"), c.text("nightpower"))
    }.distinctBy { it.date }.sortedBy { it.date }
    if (days.isEmpty()) error("该地区暂无短期预报")
    return days to o.text("reporttime")
}

class WeatherStorage(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    var fahrenheit: Boolean
        get() = prefs.getBoolean("fahrenheit", false)
        set(v) { prefs.edit().putBoolean("fahrenheit", v).apply() }
    var selected: String
        get() = prefs.getString("selected", "110100")!!
        set(v) { prefs.edit().putString("selected", v).apply() }
    fun cities(): List<City> = runCatching {
        val a = JSONArray(prefs.getString("cities", null) ?: return listOf(City("110000", "北京市", "北京市", "beijing")))
        (0 until a.length()).map { City.parse(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun saveCities(cities: List<City>) { prefs.edit().putString("cities", JSONArray(cities.map { it.json() }).toString()).apply() }
    fun cache(code: String): JSONObject? = runCatching { JSONObject(prefs.getString("cache-$code", "")!!) }.getOrNull()
    fun cache(code: String, json: JSONObject) { prefs.edit().putString("cache-$code", json.toString()).apply() }
}

class CityCatalog(context: Context) {
    val cities: List<City>
    init {
        val transliterator = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
        val a = JSONArray(context.assets.open("cities.json").bufferedReader().use { it.readText() })
        cities = (0 until a.length()).map { City.parse(a.getJSONObject(it)) }.map {
            val special = mapOf("长沙市" to "changsha", "长春市" to "changchun", "重庆市" to "chongqing", "厦门市" to "xiamen amoy", "西安市" to "xian", "六安市" to "luan", "乐山市" to "leshan", "乌鲁木齐市" to "urumqi", "香港特别行政区" to "hongkong", "澳门特别行政区" to "macau macao")
            it.copy(alias = "${it.alias} ${special[it.name].orEmpty()} ${transliterator.transliterate(it.name).filter { ch -> ch in 'a'..'z' }}")
        }
    }
    fun search(query: String): List<City> {
        val q = query.lowercase().filterNot { it.isWhitespace() || it == '\'' || it == '-' }
        if (q.isEmpty()) return emptyList()
        return cities.filter { it.name.contains(q) || it.path.contains(q) || it.alias.contains(q) || it.code == q }
            .sortedBy { if (it.name == q || it.alias.trim() == q) 0 else if (it.code.endsWith("00")) 1 else 2 }.take(100)
    }
}

class AmapRepository(private val storage: WeatherStorage) {
    private suspend fun request(endpoint: String, args: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val query = (args + ("key" to BuildConfig.AMAP_KEY) + ("output" to "JSON")).entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val connection = URL("https://restapi.amap.com/v3/$endpoint?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            if (connection.responseCode != 200) error("天气服务暂时不可用")
            val o = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (o.text("status") != "1" || o.text("infocode") != "10000") {
                val code = o.text("infocode")
                error(when (code) {
                    "10001", "10003", "10005", "10007", "10009", "10012", "10013" -> "天气服务授权异常（$code），请联系应用维护方"
                    "10004", "10010", "10019", "10020", "10021", "10044" -> "高德服务额度或调用频率超限（$code），请稍后重试"
                    else -> "高德服务返回错误（$code）"
                })
            }
            o
        } catch (e: CancellationException) { throw e }
        catch (e: java.io.IOException) { error("无法连接高德，请检查网络后重试") }
        finally { connection.disconnect() }
    }
    suspend fun locate(): City {
        val o = request("ip", emptyMap())
        val code = o.text("adcode")
        if (!code.matches(Regex("\\d{6}")) || o.text("city").isBlank()) error("IP 定位不可用，请手动添加城市")
        return City(code, o.text("city"), o.text("province"), located = true)
    }
    suspend fun resolve(city: City): String {
        if (city.located) return city.code
        val root = request("geocode/geo", mapOf("address" to city.path))
        val codes = root.optJSONArray("geocodes")
        val o = codes?.optJSONObject(0) ?: error("城市编码已变更，请重新搜索城市")
        return o.text("adcode").takeIf { it.matches(Regex("\\d{6}")) } ?: error("无法解析国内城市编码")
    }
    fun cached(city: City): Weather? = storage.cache(city.code)?.let { root ->
        val live = root.optJSONObject("base")?.let { runCatching { parseLive(it) }.getOrNull() }
        val forecast = root.optJSONObject("all")?.let { runCatching { parseForecast(it) }.getOrNull() }
        if (live == null && forecast == null) null else Weather(live, forecast?.first.orEmpty(), forecast?.second.orEmpty(), root.optLong("fetched"), cached = true)
    }
    suspend fun load(city: City): Weather = supervisorScope {
        val adcode = resolve(city)
        suspend fun safe(type: String): Result<JSONObject> = try { Result.success(request("weather/weatherInfo", mapOf("city" to adcode, "extensions" to type))) } catch(e: CancellationException) { throw e } catch(e: Exception) { Result.failure(e) }
        val base = async { safe("base") }
        val all = async { safe("all") }
        val b = base.await().mapCatching { parseLive(it); it }
        val a = all.await().mapCatching { parseForecast(it); it }
        val old = storage.cache(city.code)
        val merged = JSONObject().put("fetched", System.currentTimeMillis())
        b.getOrNull()?.let { merged.put("base", it) } ?: old?.optJSONObject("base")?.let { merged.put("base", it) }
        a.getOrNull()?.let { merged.put("all", it) } ?: old?.optJSONObject("all")?.let { merged.put("all", it) }
        if (b.isSuccess || a.isSuccess) storage.cache(city.code, merged)
        val current = cached(city) ?: error(listOfNotNull(b.exceptionOrNull()?.message, a.exceptionOrNull()?.message).distinct().joinToString("；").ifBlank { "暂无天气数据" })
        current.copy(cached = b.isFailure || a.isFailure, error = listOfNotNull(b.exceptionOrNull()?.message, a.exceptionOrNull()?.message).distinct().joinToString("；").ifBlank { null })
    }
}
