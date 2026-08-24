# AI Assistant — Android Project

A real installable Android app (Kotlin, Jetpack Compose) with:

- **Home screen icon** — opens straight to a dashboard, no terminal.
- **Touchless gesture control** — front camera + on-device MediaPipe hand tracking.
  Pinch thumb+index in the air = tap. Extend this file's gesture logic for swipe/scroll.
- **App control** — lists every installed app, launches any of them.
- **Device control** — Accessibility Service can tap, swipe, go Home/Back,
  open Recents/Notifications/Quick Settings, and read on-screen text.
- **Automation rules** — simple WHEN → DO rules stored locally, run manually
  or extend `onAccessibilityEvent` to trigger them automatically.
- **Chat** — plugs into the Anthropic API (bring your own key).
- **Floating Jarvis orb** — draggable bubble that stays on top of every app
  (like the video). Tap it, speak a command, it acts.
- **Ambient idle screen** — big pulsing orb + live clock, matching the
  opening shot of the video. Available as a preview screen, or as a real
  Android screensaver (Settings → Display → Screen saver) so it shows
  automatically while your phone is charging/idle.

## Why an Accessibility Service, not root

Android does not let any app silently control the whole device — that's by
design, to stop exactly the kind of malware this class of permission could
enable. The Accessibility Service is the *legitimate*, user-consented path
to the same capability (it's what Tasker, AutoInput, and screen readers use).
You'll enable it once, manually, in Settings → Accessibility → AI Assistant.

## Build it (you need Android Studio — this can't compile from Termux alone)

1. Install **Android Studio** (Windows/Mac/Linux) or use **Android Studio
   installed on a PC**; building a full Android app on-device isn't practical.
2. Open this folder as a project (`File > Open`).
3. Download the hand tracking model:
   `https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task`
   and place it at `app/src/main/assets/hand_landmarker.task`.
4. Click **Run** with your phone connected (USB debugging on), or
   **Build > Build Bundle(s)/APK(s) > Build APK(s)** to get an installable
   `.apk` you can transfer to your phone and tap to install
   (allow "install unknown apps" for the file manager you use).
5. First launch: tap **Open Accessibility Settings** on the dashboard and
   enable "AI Assistant" — this is what unlocks tap/swipe/app-launch control.

## File structure

```
AiAssistant/
├── build.gradle.kts                  # project-level Gradle
├── settings.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts              # dependencies: CameraX, MediaPipe, Compose, OkHttp
    └── src/main/
        ├── AndroidManifest.xml       # permissions, activities, accessibility service registration
        ├── assets/hand_landmarker.task   # ← you add this (see step 3 above)
        ├── java/com/local/aiassistant/
        │   ├── MainActivity.kt              # dashboard / home screen
        │   ├── ControlAccessibilityService.kt  # real device/app control engine
        │   ├── GestureControlActivity.kt       # camera + hand tracking → gestures
        │   ├── AppLauncherActivity.kt           # installed app list + launch
        │   ├── AutomationActivity.kt            # WHEN→DO rule engine
        │   ├── ChatActivity.kt                  # chat UI (Anthropic API)
        │   ├── OverlayOrbService.kt             # floating always-on-top orb, tap-to-talk
        │   ├── CommandDispatcher.kt             # routes voice/typed commands to actions
        │   └── AmbientActivity.kt               # idle orb+clock screen + screensaver
        └── res/
            ├── values/strings.xml, themes.xml
            ├── xml/accessibility_service_config.xml
            └── mipmap-anydpi-v26/ic_launcher.xml
```

## What I deliberately did NOT build

You asked for it to "play my games and make sure it wins." I didn't wire up
game-specific auto-win logic:
- It would need reverse-engineering each game's screen layout individually —
  there's no generic "win any game" algorithm.
- Most games' terms of service ban automated/bot play, and many have
  anti-cheat that can get accounts banned.

What *is* here is the general building block: `readVisibleText()` and
`tapNodeWithText()` in the Accessibility Service let you script "when I see
X on screen, tap Y" for your own simple/repetitive games (idle games, timing
taps, etc.) via the Automation Rules screen — you're in control of what it
targets.

## Using the floating orb

1. On the dashboard, tap **Launch Floating Orb**. First time, it'll send you
   to Settings to grant "Display over other apps" — turn that on, come back.
2. A small blue circle appears on screen, on top of whatever app you're in.
   Drag it anywhere; it stays put.
3. Tap it (don't drag) → it listens → say something like **"open camera"**,
   **"run Open Camera"** (an automation rule name), or **"go home"**.
4. It's on Android's built-in `SpeechRecognizer` — no API key needed for
   voice-to-text itself; only the Chat screen's actual AI replies need a key.

Only tap-to-talk is wired up (not always-listening "Hey Jarvis" wake word) —
continuous background listening drains battery fast and generally needs a
paid wake-word engine (e.g. Picovoice Porcupine) to be efficient. Tap-to-talk
gets you the same "no typing" experience the video shows without that cost.

## Using the ambient/idle screen

- **Preview button** on the dashboard just opens it as a normal screen.
- **Real screensaver**: tap "Set as Screensaver", pick "Jarvis" from the
  list, then it'll appear automatically when your phone is docked/charging
  and idle — same idle look as the very start of the clip you sent.

## Extending gesture control

Currently only pinch-to-tap is wired up. To add swipe-to-scroll or
fist-to-go-home, extend `onHandResult()` in `GestureControlActivity.kt`:
track landmark positions over consecutive frames, detect the direction/shape
change, and call `service.swipe(...)` or `service.goHome()`.
