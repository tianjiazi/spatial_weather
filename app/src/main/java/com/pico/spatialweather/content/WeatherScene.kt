package com.pico.spatialweather.content

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.layout.depth
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import kotlinx.coroutines.CancellationException

private class WeatherSceneResources {
    val entities = mutableListOf<Entity>()
    var root: Entity? = null
    var disposed = false
    fun close() {
        disposed = true
        entities.reversed().forEach { it.destroy() }
        entities.clear()
        root = null
    }
}

/** Genuine 3D content in a Plain window's bounded front depth, not a 2D render. */
@Composable
fun WeatherScene(condition: String, modifier: Modifier = Modifier) {
    val asset = weatherModel(condition)
    key(asset) {
        val resources = remember { WeatherSceneResources() }
        val converter = LocalPhysicalLengthConverter.current
        // Generated mesh's longest edge is 1.9 units. Uniformly fit a 260dp cube.
        val scale = converter.dpToLength(260.dp, LengthUnit.Meters) / 1.9f
        val motion = rememberInfiniteTransition(label = "weather-model")
        val bob by motion.animateFloat(-5f, 5f,
            infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "float")
        val yaw by motion.animateFloat(-9f, 9f,
            infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "turn")
        val y = converter.dpToLength(bob.dp, LengthUnit.Meters)
        val z = converter.dpToLength((-60).dp, LengthUnit.Meters)
        DisposableEffect(resources) { onDispose { resources.close() } }
        SpatialView(
            // 440dp front depth stays within Plain's 640dp bounded window.
            // Origin lies 220dp in front of the panel; -60dp places the model
            // center at 160dp while retaining the same 260dp physical size.
            modifier = modifier.depth(440.dp),
            initial = { content, _ ->
                try {
                    val root = Entity.loadSuspend("asset://models/$asset.glb")
                    if (resources.disposed) { root.destroy(); return@SpatialView }
                    resources.root = root
                    resources.entities.add(root)
                    fun lighting(entity: Entity) {
                        if (entity.components[ModelComponent::class.java] != null) {
                            entity.components.set(EnvironmentLightingSettingsComponent(.7f))
                        }
                        entity.getChildren().forEach(::lighting)
                    }
                    lighting(root)
                    listOf(
                        Triple(Color4(1f, .94f, .85f, 1f), 850f, EulerAngles(-30f, -25f, 0f)),
                        Triple(Color4(.80f, .90f, 1f, 1f), 450f, EulerAngles(-15f, 140f, 0f)),
                    ).forEach { (color, intensity, rotation) ->
                        val light = Entity()
                        light.components.set(DirectionalLightComponent(color, intensity, false))
                        light.components[TransformComponent::class.java]?.setQuaternion(rotation.toQuat())
                        resources.entities.add(light)
                        content.addEntity(light)
                    }
                    root.components[TransformComponent::class.java]?.apply {
                        scaleVector = Vector3(scale, scale, scale)
                        position = Vector3(0f, 0f, z)
                    }
                    content.addEntity(root)
                    Log.i("WeatherScene", "MODEL_READY $asset; center-front=160dp; size=260dp")
                } catch (cancelled: CancellationException) {
                    resources.close()
                    throw cancelled
                } catch (failure: Exception) {
                    resources.close()
                    Log.e("WeatherScene", "Could not load weather model $asset", failure)
                }
            },
            update = { _, _ ->
                resources.root?.components?.get(TransformComponent::class.java)?.apply {
                    scaleVector = Vector3(scale, scale, scale)
                    position = Vector3(0f, y, z)
                    setQuaternion(EulerAngles(0f, yaw, 0f).toQuat())
                }
            },
        )
    }
}
