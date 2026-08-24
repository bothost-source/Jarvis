package com.local.aiassistant

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * The floating "Jarvis" orb — a small always-on-top bubble (like the video)
 * that stays visible over every app. Drag it anywhere. Tap it to talk.
 *
 * This needs the "Display over other apps" permission, granted once from
 * the dashboard (Settings.ACTION_MANAGE_OVERLAY_PERMISSION), and runs as a
 * foreground service so Android doesn't kill it in the background.
 */
class OverlayOrbService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var orbView: View
    private lateinit var params: WindowManager.LayoutParams
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        addOrbToScreen()
    }

    private fun buildNotification(): Notification {
        val channelId = "jarvis_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis Orb", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis is active")
            .setContentText("Tap the floating orb to talk")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun addOrbToScreen() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Simple orb: a circular TextView acting as the bubble. Swap for a
        // custom animated view (pulsing ring, color states) for a closer
        // match to the video — this is the functional skeleton.
        orbView = TextView(this).apply {
            text = "J"
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(android.R.drawable.presence_online) // placeholder round drawable
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            140, 140,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager.addView(orbView, params)
        attachDragAndTapBehavior()
    }

    private fun attachDragAndTapBehavior() {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        orbView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(orbView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onOrbTapped()
                    true
                }
                else -> false
            }
        }
    }

    /** Tap-to-talk: starts listening, sends the transcribed command to the dispatcher. */
    private fun onOrbTapped() {
        if (isListening) return
        isListening = true
        setOrbState(listening = true)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    isListening = false
                    setOrbState(listening = false)
                    if (text.isNotBlank()) CommandDispatcher.handle(applicationContext, text)
                }
                override fun onError(error: Int) {
                    isListening = false
                    setOrbState(listening = false)
                }
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            startListening(intent)
        }
    }

    private fun setOrbState(listening: Boolean) {
        // Swap orb color/animation to show listening vs idle — extend with a
        // proper pulsing ring drawable for the exact look from the video.
        orbView.alpha = if (listening) 1.0f else 0.85f
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::orbView.isInitialized) windowManager.removeView(orbView)
        speechRecognizer?.destroy()
    }

    companion object {
        private const val NOTIF_ID = 42
    }
}
