package com.fahad.liquidglass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fahad.liquidglass.glass.GlassPanel
import com.fahad.liquidglass.glass.GlassRoot
import com.fahad.liquidglass.glass.LiquidBackdrop
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color.White

@Composable
fun HomeScreen() {
    GlassRoot(backdrop = { LiquidBackdrop() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .systemBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ClockCard()
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Toggle(Icons.Rounded.Wifi, "Wi-Fi", Modifier.weight(1f))
                Toggle(Icons.Rounded.Bluetooth, "Bluetooth", Modifier.weight(1f))
                Toggle(Icons.Rounded.DarkMode, "تركيز", Modifier.weight(1f))
            }
            PlayerCard()
            BrightnessCard()
            Spacer(Modifier.height(90.dp))
        }
        Dock(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
private fun ClockCard() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
        Column {
            Text(SimpleDateFormat("HH:mm", Locale.ENGLISH).format(now), color = Ink, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text(SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(now), color = Ink.copy(alpha = 0.8f), fontSize = 16.sp)
        }
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, modifier: Modifier) {
    var on by remember { mutableStateOf(true) }
    GlassPanel(
        modifier.aspectRatio(1f).clickable { on = !on },
        cornerRadius = 24.dp,
        tint = if (on) Color(0xFF7FB2FF) else Color.White,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(28.dp))
            Text(label, color = Ink, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlayerCard() {
    var playing by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("يعمل الآن", color = Ink.copy(alpha = 0.7f), fontSize = 12.sp)
            Text("Midnight Drift", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Ink, modifier = Modifier.size(36.dp))
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Ink,
                    modifier = Modifier.size(44.dp).clickable { playing = !playing })
                Icon(Icons.Rounded.SkipNext, null, tint = Ink, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun BrightnessCard() {
    var v by remember { mutableFloatStateOf(0.6f) }
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LightMode, null, tint = Ink)
            Slider(v, { v = it }, Modifier.padding(start = 12.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)))
        }
    }
}

@Composable
private fun Dock(modifier: Modifier) {
    GlassPanel(modifier, cornerRadius = 32.dp, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            listOf(Icons.Rounded.Call, Icons.Rounded.Chat, Icons.Rounded.PhotoCamera, Icons.Rounded.Language).forEach {
                Icon(it, null, tint = Ink, modifier = Modifier.size(30.dp))
            }
        }
    }
}
