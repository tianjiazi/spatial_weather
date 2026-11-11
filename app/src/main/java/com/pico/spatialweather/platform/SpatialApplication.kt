package com.pico.spatialweather.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.spatialweather.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
