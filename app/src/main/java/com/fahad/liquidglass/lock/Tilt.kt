package com.fahad.liquidglass.lock

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/** Phone tilt in -1..1 on each axis, smoothed and slowly re-centred (for parallax). */
@Composable
fun rememberTilt(enabled: Boolean = true): State<Offset> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        var base: Offset? = null
        var cur = Offset.Zero
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = Offset(e.values[0] / 9.81f, e.values[1] / 9.81f)
                val b = base?.let { it + (g - it) * 0.01f } ?: g
                base = b
                cur += (g - b - cur) * 0.18f
                state.value = Offset(cur.x.coerceIn(-1f, 1f), cur.y.coerceIn(-1f, 1f))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    return state
}
