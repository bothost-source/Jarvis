package com.local.aiassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** A single automation rule: WHEN a trigger condition happens, DO a sequence of actions. */
data class AutomationRule(
    val name: String,
    val trigger: String,     // e.g. "app_opened:com.whatsapp", "time:07:00", "text_seen:Low battery"
    val actions: List<String> // e.g. ["launch:com.spotify.music", "tap_text:Play"]
)

/**
 * Minimal local automation store + runner. Rules are saved as JSON in
 * SharedPreferences so nothing leaves the device. The Accessibility
 * Service (ControlAccessibilityService) is what actually executes actions.
 */
object AutomationStore {
    private const val PREFS = "automation_rules"
    private const val KEY = "rules_json"

    fun load(context: android.content.Context): List<AutomationRule> {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val actionsArr = obj.getJSONArray("actions")
            AutomationRule(
                name = obj.getString("name"),
                trigger = obj.getString("trigger"),
                actions = (0 until actionsArr.length()).map { actionsArr.getString(it) }
            )
        }
    }

    fun save(context: android.content.Context, rules: List<AutomationRule>) {
        val arr = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject()
            obj.put("name", rule.name)
            obj.put("trigger", rule.trigger)
            obj.put("actions", JSONArray(rule.actions))
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, arr.toString()).apply()
    }

    /** Executes one rule's action list in order via the Accessibility Service. */
    fun run(rule: AutomationRule) {
        val service = ControlAccessibilityService.instance ?: return
        rule.actions.forEach { action ->
            when {
                action.startsWith("launch:") -> service.launchApp(action.removePrefix("launch:"))
                action.startsWith("tap_text:") -> service.tapNodeWithText(action.removePrefix("tap_text:"))
                action == "home" -> service.goHome()
                action == "back" -> service.goBack()
            }
        }
    }
}

class AutomationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var rules by remember { mutableStateOf(AutomationStore.load(this)) }
            var showAdd by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Automation Rules", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Example: WHEN app opened THEN tap a button, launch another app, etc.")
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(Modifier.weight(1f)) {
                            items(rules) { rule ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(rule.name, style = MaterialTheme.typography.titleMedium)
                                        Text("Trigger: ${rule.trigger}")
                                        Text("Actions: ${rule.actions.joinToString(" → ")}")
                                        Button(onClick = { AutomationStore.run(rule) }) {
                                            Text("Run now")
                                        }
                                    }
                                }
                            }
                        }
                        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Add example rule")
                        }
                    }
                }
            }

            // Adds a demo rule so the list isn't empty on first run.
            LaunchedEffect(showAdd) {
                if (showAdd) {
                    val newRules = rules + AutomationRule(
                        name = "Open Camera",
                        trigger = "manual",
                        actions = listOf("launch:com.android.camera")
                    )
                    AutomationStore.save(this@AutomationActivity, newRules)
                    rules = newRules
                    showAdd = false
                }
            }
        }
    }
}
