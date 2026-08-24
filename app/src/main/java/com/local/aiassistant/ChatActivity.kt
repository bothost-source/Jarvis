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
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ChatMessage(val role: String, val text: String)

/**
 * A plain chat UI. It's wired to call the Anthropic Messages API directly,
 * but you must supply your own API key — never hardcode a key in shipped
 * code. Put it in local.properties (gitignored) and read it via BuildConfig,
 * or prompt for it in-app and store it with EncryptedSharedPreferences.
 */
class ChatActivity : ComponentActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var input by remember { mutableStateOf("") }
            val messages = remember { mutableStateListOf<ChatMessage>() }
            val scope = rememberCoroutineScope()

            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Chat", style = MaterialTheme.typography.headlineSmall)
                        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            items(messages) { msg ->
                                Text("${if (msg.role == "user") "You" else "AI"}: ${msg.text}")
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Row {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Message") }
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val text = input
                                if (text.isBlank()) return@Button
                                messages.add(ChatMessage("user", text))
                                input = ""
                                scope.launch { sendToApi(text) { reply -> messages.add(ChatMessage("assistant", reply)) } }
                            }) { Text("Send") }
                        }
                    }
                }
            }
        }
    }

    /** Calls the Anthropic Messages API. Replace API_KEY with your own key at runtime. */
    private suspend fun sendToApi(userText: String, onReply: (String) -> Unit) {
        val apiKey = getSharedPreferences("secrets", 0).getString("anthropic_api_key", null)
        if (apiKey.isNullOrBlank()) {
            onReply("No API key set. Add one in Settings to enable real chat replies.")
            return
        }

        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 1000)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userText)
            }))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(RequestBody.create("application/json".toMediaType(), body.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { onReply("Network error: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val text = json.optJSONArray("content")?.optJSONObject(0)?.optString("text")
                    ?: "No reply"
                runOnUiThread { onReply(text) }
            }
        })
    }
}
