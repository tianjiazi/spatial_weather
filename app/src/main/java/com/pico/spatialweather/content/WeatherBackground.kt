package com.pico.spatialweather.content

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Use the model's classifier so every supported label has a matching scene.
fun weatherBackground(condition: String): String = weatherModel(condition).substringBefore('_')

private class BackgroundImages(private val assets: AssetManager) {
    private val cache = LinkedHashMap<String, ImageBitmap>(4, .75f, true)

    // One loader per window; bounded to three decoded images (about 13 MB).
    @Synchronized
    fun load(family: String): ImageBitmap? {
        cache[family]?.let { return it }
        val image = try {
            assets.open("backgrounds/$family.webp").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: java.io.IOException) {
            null
        }
        if (image != null) {
            cache[family] = image
            if (cache.size > 3) cache.remove(cache.keys.first())
            // Do not recycle: an image may still be used by the fade animation.
        }
        return image
    }
}

@Composable
fun WeatherBackground(condition: String, modifier: Modifier = Modifier) {
    val assets = LocalContext.current.applicationContext.assets
    val images = remember(assets) { BackgroundImages(assets) }
    val family = weatherBackground(condition)
    var displayed by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(family, images) {
        val loaded = withContext(Dispatchers.IO) {
            images.load(family) ?: images.load("unknown")
        }
        // Keep the previous image until the replacement has decoded.
        if (loaded != null) displayed = loaded
    }
    Box(modifier.background(Color(0xFFEAF2FA))) {
        Crossfade(targetState = displayed, animationSpec = tween(450), label = "weather landscape") { image ->
            if (image != null) Image(
                bitmap = image, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        // Protect the city/temperature and the lower information cards.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            Color.White.copy(alpha = .32f), Color.White.copy(alpha = .12f), Color.Transparent,
        ))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.White.copy(alpha = .08f),
            .48f to Color.Transparent,
            .75f to Color(0xFFF4F7FB).copy(alpha = .62f),
            1f to Color(0xFFF4F7FB).copy(alpha = .86f),
        )))
    }
}
