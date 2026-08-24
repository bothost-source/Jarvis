package com.local.aiassistant

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Lists every launchable app on the device and opens it through the Accessibility Service. */
class AppLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("App Control", style = MaterialTheme.typography.headlineSmall)
                        Text("Tap any app to launch it")
                        Spacer(Modifier.height(12.dp))
                        LazyColumn {
                            items(apps) { app: ApplicationInfo ->
                                ListItem(
                                    headlineContent = { Text(app.loadLabel(pm).toString()) },
                                    supportingContent = { Text(app.packageName) },
                                    modifier = Modifier.clickable {
                                        val ok = ControlAccessibilityService.instance
                                            ?.launchApp(app.packageName) ?: false
                                        if (!ok) {
                                            // fall back to a normal launch if the
                                            // accessibility service isn't enabled yet
                                            pm.getLaunchIntentForPackage(app.packageName)
                                                ?.let { startActivity(it) }
                                        }
                                    }
                                )
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}
