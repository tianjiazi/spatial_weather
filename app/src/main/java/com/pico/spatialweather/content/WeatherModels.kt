package com.pico.spatialweather.content

// Generated alongside the original GLB collection by tools/generate_weather_models.py.
private val weatherModels = mapOf(
    "晴" to "sun_00",
    "多云" to "cloud_00",
    "少云" to "cloud_01",
    "晴间多云" to "cloud_02",
    "阴" to "overcast_00",
    "阵雨" to "rain_00",
    "小雨" to "rain_01",
    "中雨" to "rain_02",
    "大雨" to "rain_03",
    "暴雨" to "rain_04",
    "大暴雨" to "rain_05",
    "特大暴雨" to "rain_06",
    "小雨-中雨" to "rain_07",
    "中雨-大雨" to "rain_08",
    "大雨-暴雨" to "rain_09",
    "暴雨-大暴雨" to "rain_10",
    "大暴雨-特大暴雨" to "rain_11",
    "强阵雨" to "rain_12",
    "极端降雨" to "rain_13",
    "毛毛雨/细雨" to "rain_14",
    "雨" to "rain_15",
    "冻雨" to "rain_16",
    "雷阵雨" to "thunder_00",
    "强雷阵雨" to "thunder_01",
    "雷阵雨并伴有冰雹" to "thunder_02",
    "雨夹雪" to "sleet_00",
    "阵雪" to "snow_00",
    "小雪" to "snow_01",
    "中雪" to "snow_02",
    "大雪" to "snow_03",
    "暴雪" to "snow_04",
    "小雪-中雪" to "snow_05",
    "中雪-大雪" to "snow_06",
    "大雪-暴雪" to "snow_07",
    "雪" to "snow_08",
    "弱高吹雪" to "snow_09",
    "吹雪" to "snow_10",
    "雾" to "fog_00",
    "轻雾" to "fog_01",
    "大雾" to "fog_02",
    "浓雾" to "fog_03",
    "强浓雾" to "fog_04",
    "特强浓雾" to "fog_05",
    "中度雾" to "fog_06",
    "霾" to "haze_00",
    "中度霾" to "haze_01",
    "重度霾" to "haze_02",
    "严重霾" to "haze_03",
    "浮尘" to "dust_00",
    "扬沙" to "dust_01",
    "沙尘暴" to "dust_02",
    "强沙尘暴" to "dust_03",
    "平静" to "wind_00",
    "微风" to "wind_01",
    "和风" to "wind_02",
    "清风" to "wind_03",
    "强风/劲风" to "wind_04",
    "疾风" to "wind_05",
    "大风" to "wind_06",
    "烈风" to "wind_07",
    "风暴" to "wind_08",
    "狂爆风" to "wind_09",
    "飓风" to "wind_10",
    "热带风暴" to "wind_11",
    "飑" to "wind_12",
    "龙卷风" to "tornado_00",
    "热" to "hot_00",
    "冷" to "cold_00",
    "未知" to "unknown_00",
)

fun weatherModel(condition: String): String {
    val normalized = condition.trim().replace("－", "-").replace("转", "-").replace("到", "-")
    weatherModels[normalized]?.let { return it }
    val family = when {
        normalized.contains("雷") -> "thunder"
        normalized.contains("雨夹雪") -> "sleet"
        normalized.contains("雪") -> "snow"
        normalized.contains("雨") -> "rain"
        normalized.contains("沙") || normalized.contains("尘") -> "dust"
        normalized.contains("霾") -> "haze"
        normalized.contains("雾") -> "fog"
        normalized.contains("龙卷") -> "tornado"
        normalized.contains("风") || normalized.contains("飑") -> "wind"
        normalized.contains("云") -> "cloud"
        normalized.contains("晴") -> "sun"
        normalized.contains("阴") -> "overcast"
        normalized.contains("热") -> "hot"
        normalized.contains("冷") -> "cold"
        else -> "unknown"
    }
    return "${family}_00"
}
