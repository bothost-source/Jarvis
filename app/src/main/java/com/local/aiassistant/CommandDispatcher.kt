package com.local.aiassistant

import android.content.Context
import android.widget.Toast

/**
 * Turns a plain-English command (from voice or typed chat) into an action:
 * "open spotify"        -> launches the app
 * "run <automation name>" -> runs a saved automation rule
 * anything else          -> falls through to the chat AI for a spoken/typed reply
 *
 * This is intentionally simple pattern matching — swap in an LLM call
 * (reusing the same request code as ChatActivity) for true natural-language
 * understanding once you're ready; the routing shape stays the same.
 */
object CommandDispatcher {

    fun handle(context: Context, rawText: String) {
        val text = rawText.trim().lowercase()

        if (text.startsWith("open ")) {
            val appName = text.removePrefix("open ").trim()
            val launched = tryLaunchByName(context, appName)
            toast(context, if (launched) "Opening $appName" else "Couldn't find an app called \"$appName\"")
            return
        }

        if (text.startsWith("run ")) {
            val ruleName = text.removePrefix("run ").trim()
            val rule = AutomationStore.load(context).find { it.name.lowercase() == ruleName }
            if (rule != null) {
                AutomationStore.run(rule)
                toast(context, "Running \"${rule.name}\"")
            } else {
                toast(context, "No automation rule named \"$ruleName\"")
            }
            return
        }

        when (text) {
            "go home", "home" -> ControlAccessibilityService.instance?.goHome()
            "go back", "back" -> ControlAccessibilityService.instance?.goBack()
            else -> {
                // Fall through to the AI chat backend for a conversational reply.
                toast(context, "Asking Jarvis: \"$rawText\"")
                // Wire this to ChatActivity's sendToApi logic (extract it to a
                // shared function) if you want spoken replies from the orb too.
            }
        }
    }

    private fun tryLaunchByName(context: Context, spokenName: String): Boolean {
        val pm = context.packageManager
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcherIntent, 0).firstOrNull {
            it.activityInfo.applicationInfo.loadLabel(pm).toString().lowercase().contains(spokenName)
        } ?: return false

        val pkg = match.activityInfo.applicationInfo.packageName
        val ok = ControlAccessibilityService.instance?.launchApp(pkg) ?: false
        if (!ok) pm.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
        return true
    }

    private fun toast(context: Context, msg: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
