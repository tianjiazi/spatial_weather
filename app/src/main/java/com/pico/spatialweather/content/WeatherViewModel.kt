package com.pico.spatialweather.content

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WeatherState(
    val cities: List<City> = emptyList(), val selected: String = "", val weather: Weather? = null,
    val loading: Boolean = false, val message: String? = null,
    val fahrenheit: Boolean = false, val page: String = "home",
    val query: String = "", val results: List<City> = emptyList()
) { val city get() = cities.firstOrNull { it.id == selected } }

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = WeatherStorage(app)
    private val repository = AmapRepository(storage)
    private var catalog: CityCatalog? = null
    private var request: Job? = null
    private var searchJob: Job? = null
    private val mutable = MutableStateFlow(WeatherState(storage.cities(), storage.selected, fahrenheit = storage.fahrenheit))
    val state = mutable.asStateFlow()
    fun cachedWeather(city: City): Weather? = repository.cached(city)
    init {
        viewModelScope.launch { catalog = withContext(Dispatchers.Default) { CityCatalog(app) }; search(mutable.value.query) }
        if (mutable.value.city == null) mutable.update { it.copy(selected = it.cities.firstOrNull()?.id.orEmpty()) }
        refresh()
    }
    private fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        mutable.update { it.copy(query = "", results = emptyList()) }
    }
    fun page(value: String) {
        if (value == "home" || (value == "cities" && mutable.value.page != "cities")) clearSearch()
        mutable.update { it.copy(page = value, message = null) }
    }
    fun select(city: City) {
        if (city.id == mutable.value.selected && mutable.value.page == "home") return
        clearSearch()
        request?.cancel()
        storage.selected = city.id
        mutable.update { it.copy(selected = city.id, weather = repository.cached(city), page = "home", message = null) }
        refresh()
    }
    fun add(city: City) {
        if (mutable.value.cities.any { it.id == city.id }) { mutable.update { it.copy(message = "该城市已添加") }; return }
        val cities = mutable.value.cities + city
        storage.saveCities(cities); mutable.update { it.copy(cities = cities) }; select(city)
    }
    fun delete(city: City) {
        val cities = mutable.value.cities.filterNot { it.id == city.id }
        storage.saveCities(cities); mutable.update { it.copy(cities = cities) }
        if (mutable.value.selected == city.id) {
            request?.cancel()
            storage.selected = cities.firstOrNull()?.id.orEmpty()
            mutable.update { it.copy(selected = storage.selected, weather = null, loading = false) }; refresh()
        }
    }
    fun move(city: City, delta: Int) {
        val list = mutable.value.cities.toMutableList(); val i = list.indexOfFirst { it.id == city.id }
        if (i < 0 || i + delta !in list.indices) return
        list.add(i + delta, list.removeAt(i)); storage.saveCities(list); mutable.update { it.copy(cities = list) }
    }
    fun search(query: String) {
        mutable.update { it.copy(query = query) }; searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { catalog?.search(query).orEmpty() }
            mutable.update { if (it.query == query) it.copy(results = result) else it }
        }
    }
    fun unit(fahrenheit: Boolean) { storage.fahrenheit = fahrenheit; mutable.update { it.copy(fahrenheit = fahrenheit) } }
    fun locate() {
        request?.cancel()
        request = viewModelScope.launch {
            mutable.update { it.copy(loading = true, message = "正在使用高德 IP 定位（城市级）") }
            try {
                val city = repository.locate()
                val list = mutable.value.cities.filterNot { it.located } + city
                storage.saveCities(list); mutable.update { it.copy(cities = list, loading = false) }; select(city)
            } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(loading = false, message = e.message) } }
        }
    }
    fun refresh() {
        request?.cancel()
        val s = mutable.value
        val city = s.city ?: return
        request = viewModelScope.launch {
            mutable.update { it.copy(loading = true, weather = it.weather ?: repository.cached(city), message = null) }
            try {
                val weather = repository.load(city)
                mutable.update { it.copy(weather = weather, loading = false, message = weather.error) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                mutable.update { it.copy(loading = false, weather = it.weather?.copy(cached = true), message = e.message ?: "更新失败，请重试") }
            }
        }
    }
}
