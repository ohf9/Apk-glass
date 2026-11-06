package com.fahad.liquidglass

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fahad.liquidglass.lock.LockService
import com.fahad.liquidglass.ui.HomeScreen
import com.fahad.liquidglass.ui.HubScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        val crash = crashFile.takeIf { it.exists() }?.readText()
        setContent {
            var report by remember { mutableStateOf(crash) }
            report?.let { CrashReport(it) { crashFile.delete(); report = null }; return@setContent }
            var demo by remember { mutableStateOf(false) }
            BackHandler(demo) { demo = false }
            if (demo) HomeScreen() else HubScreen(onOpenDemo = { demo = true })
        }
    }

    override fun onResume() {
        super.onResume()
        LockService.sync(this) // re-attach after the user grants "display over other apps"
    }
}

@androidx.compose.runtime.Composable
private fun CrashReport(text: String, onDismiss: () -> Unit) {
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF111118))
            .systemBarsPadding().padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
    ) {
        androidx.compose.material3.Text("صار خطأ في المرة اللي فاتت. انسخه وأرسله لـ Claude:", color = androidx.compose.ui.graphics.Color.White)
        androidx.compose.foundation.layout.Row {
            androidx.compose.material3.TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(text)) }) { androidx.compose.material3.Text("نسخ") }
            androidx.compose.material3.TextButton(onDismiss) { androidx.compose.material3.Text("متابعة") }
        }
        androidx.compose.material3.Text(text, color = androidx.compose.ui.graphics.Color(0xFFFF8A80), fontSize = 11.sp)
    }
}
