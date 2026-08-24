package com.local.aiassistant

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * The pulsing orb + clock composable — matches the idle screen shown at the
 * start of the video (big centered orb, time underneath).
 */
@Composable
fun JarvisAmbientScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            kotlinx.coroutines.delay(1000)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF05070C)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size((160 * pulse).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565FF).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B1E4D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("JARVIS", color = Color(0xFF6FA8FF), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(time, color = Color.White, fontSize = 34.sp)
            Text("Tap anywhere to wake", color = Color(0xFF6FA8FF), fontSize = 12.sp)
        }
    }
}

private fun currentTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/** Launchable version — trigger this from the dashboard for an "ambient mode" button. */
class AmbientActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { JarvisAmbientScreen() }
        }
    }
}

/**
 * Native Android screensaver version. Enable under
 * Settings > Display > Screen saver > Jarvis, and it shows automatically
 * while charging/docked — exactly like the idle screen in the video.
 */
class JarvisDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        setContentView(androidx.compose.ui.platform.ComposeView(this).apply {
            setContent { MaterialTheme { JarvisAmbientScreen() } }
        })
    }
}
