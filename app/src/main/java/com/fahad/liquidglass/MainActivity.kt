package com.fahad.liquidglass

import android.os.Bundle
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
        setContent {
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
