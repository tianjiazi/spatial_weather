package com.pico.spatialweather.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.design.windows.AlertDialog
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatialweather.BuildConfig
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomePage(modifier: Modifier = Modifier, vm: WeatherViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var locateDialog by rememberSaveable { mutableStateOf(false) }
    var visibleCondition by remember { mutableStateOf(s.weather?.live?.weather.orEmpty()) }
    LaunchedEffect(s.selected, s.weather?.live?.weather, s.page) {
        visibleCondition = s.weather?.live?.weather.orEmpty()
    }
    Box(modifier.fillMaxSize()) {
        WeatherBackground(if (s.page == "home") visibleCondition else "", Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.page == "home") {
                TitleBar(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentPadding = PaddingValues(0.dp),
                    title = {
                        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日  EEEE", Locale.CHINA)),
                            modifier = Modifier.fillMaxWidth(),
                            color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingActions = {
                        Button(onClick = { vm.refresh() }, enabled = !s.loading,
                            colors = secondaryButtonColors()) { Text(if (s.loading) "更新中…" else "刷新") }
                        Button(onClick = { vm.page("cities") }) { Text("城市管理") }
                        Button(onClick = { settings = true }, colors = secondaryButtonColors()) { Text("设置") }
                    },
                )
            } else {
                TitleBar(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentPadding = PaddingValues(0.dp),
                    leadingActions = {
                        IconButton(
                            onClick = { vm.page("home") },
                            modifier = Modifier.size(48.dp),
                            size = IconButtonDefaults.iconButtonSize(48.dp),
                        ) {
                            Icon(
                                painter = painterResource(com.pico.spatial.ui.design.R.drawable.ic_sui_close),
                                contentDescription = "关闭我的城市",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    title = {
                        Text("我的城市", modifier = Modifier.fillMaxWidth(),
                            style = PicoTheme.typography.headlineLarge)
                    },
                )
            }
            if (s.page == "home") {
                CityWeatherPager(s, vm) { visibleCondition = it }
            } else {
                CitiesPage(s, vm, { deleteId = it }, { locateDialog = true })
            }
        }
    }
    if (settings) Settings(s, vm) { settings = false }
    val deleting = s.cities.firstOrNull { it.id == deleteId }
    if (deleting != null) AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text("删除${deleting.name}？", style = PicoTheme.typography.headlineLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        content = { Text("删除后可随时重新添加。", style = PicoTheme.typography.bodyMedium,
            color = PicoTheme.colorScheme.labelSecondary) },
        buttons = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Button(onClick = { deleteId = null }, colors = secondaryButtonColors()) { Text("取消") }
            Button(onClick = { vm.delete(deleting); deleteId = null },
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.error,
                    contentColor = PicoTheme.colorScheme.labelPrimaryLight)) { Text("删除") }
        } }
    )
    if (locateDialog) AlertDialog(
        onDismissRequest = { locateDialog = false },
        title = { Text("使用高德 IP 定位", style = PicoTheme.typography.headlineLarge) },
        content = { Text("将向高德发送网络请求，用当前公网 IP 估计所在城市。无需精确位置权限；VPN、代理或网络出口可能导致城市不准确。你也可以手动添加城市。",
            style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary) },
        buttons = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Button(onClick = { locateDialog = false }, colors = secondaryButtonColors()) { Text("取消") }
            Button(onClick = { locateDialog = false; vm.locate() }) { Text("定位城市") }
        } }
    )
}

@Composable
private fun ColumnScope.CityWeatherPager(s: WeatherState, vm: WeatherViewModel, onVisibleWeather: (String) -> Unit) {
    if (s.cities.isEmpty()) {
        WeatherHome(s, { vm.page("cities") }, Modifier.weight(1f))
        return
    }
    val pager = rememberPagerState(
        initialPage = s.cities.indexOfFirst { it.id == s.selected }.coerceAtLeast(0),
        pageCount = { s.cities.size },
    )
    val latestCities by rememberUpdatedState(s.cities)
    val latestState by rememberUpdatedState(s)
    val latestVisibleWeather by rememberUpdatedState(onVisibleWeather)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, s.weather?.fetched, s.selected, s.cities.map { it.id }) {
        snapshotFlow { pager.currentPage }.collectLatest { index ->
            val city = latestCities.getOrNull(index)
            val weather = if (city?.id == latestState.selected) latestState.weather
                else city?.let(vm::cachedWeather)
            latestVisibleWeather(weather?.live?.weather.orEmpty())
        }
    }
    LaunchedEffect(s.selected, s.cities.map { it.id }) {
        val index = s.cities.indexOfFirst { it.id == s.selected }
        if (index >= 0 && !pager.isScrollInProgress && pager.currentPage != index) pager.scrollToPage(index)
    }
    LaunchedEffect(pager) {
        var dragged = false
        snapshotFlow { pager.isScrollInProgress }.collectLatest { moving ->
            if (moving) dragged = true
            else if (dragged) {
                dragged = false
                latestCities.getOrNull(pager.settledPage)?.let(vm::select)
            }
        }
    }
    HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(),
        key = { s.cities[it].id }, userScrollEnabled = s.cities.size > 1) { index ->
        val city = s.cities[index]
        val cached = remember(city.code, s.weather?.fetched) { vm.cachedWeather(city) }
        val pageState = if (city.id == s.selected) s else s.copy(
            selected = city.id, weather = cached, loading = false, message = null,
        )
        WeatherHome(pageState, { vm.page("cities") }, Modifier.fillMaxSize())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        PageControl(
            currentIndex = pager.currentPage.coerceIn(s.cities.indices),
            totalDots = s.cities.size,
            onClickAction = { index -> scope.launch { pager.animateScrollToPage(index) } },
            colors = PageControlDefaults.pageControlColors(
                highLightColor = PicoTheme.colorScheme.interaction,
                normalColor = PicoTheme.colorScheme.labelTertiary,
            ),
        )
    }
}

@Composable
private fun WeatherHome(s: WeatherState, cities: () -> Unit, modifier: Modifier) {
    if (s.city == null) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("添加你的城市", style = PicoTheme.typography.headlineLarge)
            Text("添加城市后即可查看实况天气和未来3天预报。",
                style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
            Button(onClick = cities) { Text("添加城市") }
        }
        return
    }
    val weather = s.weather
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.city?.name ?: "添加你的城市",
                    style = PicoTheme.typography.headlineLarge.copy(fontSize = 48.sp, lineHeight = 58.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.city?.path.orEmpty() + if (s.city?.located == true) " · IP 定位" else "",
                    color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(temperature(weather?.live?.temp, s.fahrenheit).removeSuffix("°"),
                        style = PicoTheme.typography.displayLarge.copy(fontSize = 124.sp, lineHeight = 136.sp))
                    Text(if (s.fahrenheit) "°F" else "°C",
                        modifier = Modifier.padding(bottom = 18.dp),
                        style = PicoTheme.typography.titleLarge.copy(fontSize = 32.sp, lineHeight = 40.sp))
                }
            }
            Column(Modifier.width(320.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("当前天气", color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodyMedium)
                Text(weather?.live?.weather?.ifBlank { "—" } ?: "等待天气数据",
                    style = PicoTheme.typography.headlineLarge.copy(fontSize = 42.sp, lineHeight = 50.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                weather?.days?.firstOrNull()?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("白天", color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodyMedium)
                            Text(temperature(it.dayTemp, s.fahrenheit), style = PicoTheme.typography.titleLarge.copy(fontSize = 30.sp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("夜间", color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodyMedium)
                            Text(temperature(it.nightTemp, s.fahrenheit), style = PicoTheme.typography.titleLarge.copy(fontSize = 30.sp))
                        }
                    }
                }
            }
            WeatherScene(weather?.live?.weather.orEmpty(), Modifier.width(440.dp).fillMaxHeight())
        }
        val notice = s.message
        if (notice != null) {
            Text(notice, color = PicoTheme.colorScheme.alert,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                style = PicoTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("温度", temperature(weather?.live?.temp, s.fahrenheit).let {
                if (it == "—") it else it + if (s.fahrenheit) "F" else "C"
            }, Modifier.weight(1f))
            Metric("相对湿度", weather?.live?.humidity?.let { "${it.toInt()}%" } ?: "—", Modifier.weight(1f))
            Metric("风向", direction(weather?.live?.wind.orEmpty()), Modifier.weight(1f))
            Metric("风力", power(weather?.live?.power.orEmpty()), Modifier.weight(1f))
        }
        Text("未来3天预报", modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            style = PicoTheme.typography.titleLarge)
        if (weather?.days.isNullOrEmpty()) Text("暂无预报",
            color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodyMedium)
        if (!weather?.days.isNullOrEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            weather!!.days.forEachIndexed { index, day ->
                Column(Modifier.weight(1f).background(PicoTheme.colorScheme.fillTertiary, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val date = LocalDate.parse(day.date)
                    Text(date.format(DateTimeFormatter.ofPattern("M月d日  E", Locale.CHINA)) +
                        if (index == 0) " · 今天" else "", style = PicoTheme.typography.titleMedium)
                    ForecastPeriod("白天", day.dayWeather, temperature(day.dayTemp, s.fahrenheit),
                        day.dayWind, day.dayPower, isNight = false)
                    ForecastPeriod("夜间", day.nightWeather, temperature(day.nightTemp, s.fahrenheit),
                        day.nightWind, day.nightPower, isNight = true)
                }
            }
        }
        if (weather != null) {
            Text("数据来源：高德地图  ·  实况发布 ${weather.live?.published?.ifBlank { "—" } ?: "—"}  ·  预报发布 ${weather.forecastTime.ifBlank { "—" }}",
                modifier = Modifier.padding(top = 4.dp),
                color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ForecastPeriod(
    label: String, weather: String, temperature: String, wind: String, windPower: String, isNight: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeatherSymbol(weather, Modifier.size(36.dp), isNight)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$label ${weather.ifBlank { "—" }}", style = PicoTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(temperature, modifier = Modifier.alignByBaseline(),
                    style = PicoTheme.typography.bodyMedium, maxLines = 1)
                Text("${direction(wind)}  ${power(windPower)}",
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier) {
    Column(modifier.background(PicoTheme.colorScheme.fillTertiary, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodyMedium)
        Text(value, style = PicoTheme.typography.titleLarge)
    }
}

@Composable
private fun ColumnScope.CitiesPage(s: WeatherState, vm: WeatherViewModel, deleting: (String) -> Unit, locate: () -> Unit) {
    SearchField(value = s.query, onValueChange = vm::search, onSearch = { vm.search(s.query) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("搜索国内城市 / 区县 / 拼音") })
    s.message?.let { Text(it, color = PicoTheme.colorScheme.alert) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = locate, enabled = !s.loading, colors = secondaryButtonColors()) { Text("IP 定位城市") }
        Text("热门", color = PicoTheme.colorScheme.labelSecondary)
        listOf(City("110000", "北京市", "北京市", "beijing"), City("310000", "上海市", "上海市", "shanghai"), City("440100", "广州市", "广东省广州市", "guangzhou"), City("440300", "深圳市", "广东省深圳市", "shenzhen"), City("330100", "杭州市", "浙江省杭州市", "hangzhou")).forEach { city ->
            ButtonChip(label = { Text(city.name.removeSuffix("市")) }, onClick = { vm.add(city) })
        }
    }
    val cityItems = if (s.query.isBlank()) s.cities else s.results
    val listState = rememberLazyListState()
    LaunchedEffect(s.query) { listState.scrollToItem(0) }
    if (cityItems.isEmpty()) {
        Column(Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (s.query.isBlank()) "还没有添加城市" else "没有匹配城市",
                style = PicoTheme.typography.titleLarge)
            Text(if (s.query.isBlank()) "搜索国内城市，或选择上方热门城市。" else "请尝试中文全名或全拼，并核对所在行政区。",
                style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        }
    } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
        contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cityItems, key = { it.id }) { city ->
            val itemShape = RoundedCornerShape(24.dp)
            ListItem(
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).then(
                    if (s.query.isBlank()) Modifier.clip(itemShape).clickable(
                        onClickLabel = "查看${city.name}天气", role = Role.Button,
                    ) { vm.select(city) } else Modifier
                ),
                shape = itemShape,
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                headlineContent = {
                    Text(city.name + if (city.located) " · IP 定位" else "",
                        style = PicoTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(city.path, style = PicoTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (s.query.isNotBlank()) {
                            Button(onClick = { vm.add(city) },
                                enabled = s.cities.none { it.id == city.id }) {
                                Text(if (s.cities.any { it.id == city.id }) "已添加" else "添加")
                            }
                        } else {
                            Button(onClick = { vm.move(city, -1) }, enabled = s.cities.firstOrNull()?.id != city.id,
                                colors = secondaryButtonColors()) { Text("上移") }
                            Button(onClick = { vm.move(city, 1) }, enabled = s.cities.lastOrNull()?.id != city.id,
                                colors = secondaryButtonColors()) { Text("下移") }
                            Button(onClick = { deleting(city.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary,
                                    contentColor = PicoTheme.colorScheme.error)) { Text("删除") }
                        }
                    }
                },
            )
        }
    }
    Text("共 ${cityItems.size} 个城市", color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.bodySmall)
}

@Composable
private fun Settings(s: WeatherState, vm: WeatherViewModel, close: () -> Unit) {
    Sheet(onDismissRequest = close, title = { Text("设置") }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("温度单位", style = PicoTheme.typography.titleMedium)
            SegmentControl(Modifier.fillMaxWidth()) {
                SegmentItem(selected = !s.fahrenheit, title = { Text("摄氏 °C") }, onClick = { vm.unit(false) })
                SegmentItem(selected = s.fahrenheit, title = { Text("华氏 °F") }, onClick = { vm.unit(true) })
            }
            Text("版本号：V${BuildConfig.VERSION_NAME}", color = PicoTheme.colorScheme.labelSecondary,
                style = PicoTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun secondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = PicoTheme.colorScheme.fillTertiary,
    contentColor = PicoTheme.colorScheme.labelPrimary,
)

@Composable
private fun WeatherSymbol(weather: String, modifier: Modifier, isNight: Boolean = false) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (weather.contains("晴") || weather.contains("云")) {
            if (isNight) {
                val moon = Path().apply {
                    moveTo(w * .72f, h * .08f)
                    cubicTo(w * .29f, h * .02f, w * .26f, h * .62f, w * .65f, h * .65f)
                    cubicTo(w * .83f, h * .67f, w * .97f, h * .54f, w * .98f, h * .40f)
                    cubicTo(w * .65f, h * .53f, w * .51f, h * .26f, w * .72f, h * .08f)
                    close()
                }
                drawPath(moon, Color(0xFFA3B5E8))
            } else drawCircle(Color(0xFFFFCA62), w * .23f, Offset(w * .65f, h * .3f))
        }
        if (!weather.contains("晴")) {
            val c = Color(0xFF91B7D5)
            drawCircle(c, w * .23f, Offset(w * .3f, h * .48f))
            drawCircle(c, w * .28f, Offset(w * .5f, h * .37f))
            drawCircle(c, w * .2f, Offset(w * .72f, h * .5f))
            drawRoundRect(c, Offset(w * .13f, h * .45f), Size(w * .72f, h * .25f), androidx.compose.ui.geometry.CornerRadius(w * .13f))
        }
        if (weather.contains("雨")) (0..2).forEach { i -> drawLine(Color(0xFF468DCD), Offset(w * (.25f + i * .22f), h * .78f), Offset(w * (.20f + i * .22f), h * .95f), w * .045f) }
        if (weather.contains("雪")) (0..2).forEach { i -> drawCircle(Color(0xFF91B7D5), w * .045f, Offset(w * (.25f + i * .22f), h * .87f)) }
    }
}
