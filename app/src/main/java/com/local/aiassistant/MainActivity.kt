package com.local.aiassistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The dashboard you land on when you tap the home-screen icon. No terminal,
 * no typing — everything is a button.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var serviceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled()) }

            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text("AI Assistant", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))

                        if (!serviceEnabled) {
                            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Device control is OFF")
                                    Text("Enable the Accessibility Service once so gestures and automation can actually act on your phone.")
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    }) { Text("Open Accessibility Settings") }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        DashboardButton("✋ Touchless Gesture Control") {
                            startActivity(Intent(this@MainActivity, GestureControlActivity::class.java))
                        }
                        DashboardButton("📱 App Control") {
                            startActivity(Intent(this@MainActivity, AppLauncherActivity::class.java))
                        }
                        DashboardButton("⚙️ Automation Rules") {
                            startActivity(Intent(this@MainActivity, AutomationActivity::class.java))
                        }
                        DashboardButton("💬 Chat") {
                            startActivity(Intent(this@MainActivity, ChatActivity::class.java))
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("Jarvis Orb (floating widget)", style = MaterialTheme.typography.titleMedium)

                        DashboardButton("🔵 Launch Floating Orb") {
                            if (Settings.canDrawOverlays(this@MainActivity)) {
                                startForegroundService(Intent(this@MainActivity, OverlayOrbService::class.java))
                            } else {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            }
                        }
                        DashboardButton("🕐 Ambient Idle Screen (preview)") {
                            startActivity(Intent(this@MainActivity, AmbientActivity::class.java))
                        }
                        DashboardButton("🖥️ Set as Screensaver") {
                            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time we come back from Settings
        recreate()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/.ControlAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.contains(expected)
    }
}

@Composable
fun DashboardButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label)
    }
}
