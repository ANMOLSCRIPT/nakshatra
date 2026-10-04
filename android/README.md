# Nakshatra for Android

The native Android companion of the Nakshatra website. It talks to the same
server (`backend/cloud_server.py`) over the same API, so the archive, the answer
engine and the live voice pipeline are shared with the website and the ESP32.

| | |
|---|---|
| Language / UI | Kotlin 2.2, Jetpack Compose, Material 3 |
| Navigation | Navigation Compose, single activity |
| State | `ViewModel` + `StateFlow`; a port of the website's `voice.js` state machine |
| Networking | Retrofit + OkHttp (REST), OkHttp WebSocket (`/ws/ui`), kotlinx.serialization |
| Images | Coil, for a topic's optional photograph (`/assets/heritage/…`) |
| Speech | Android `TextToSpeech`, Indian English when installed |
| Build | Android Gradle Plugin 9.1, Gradle 9.3.1, JDK 17+, compile SDK 36, min SDK 26 |

## Run it

1. Start the server on your computer: `python backend/cloud_server.py`.
2. Open this `android/` folder in Android Studio and press **Run**, or:

```bash
cd android
./gradlew installDebug          # needs an emulator or a phone with USB debugging
```

On the **emulator** the app finds the server by itself (`http://10.0.2.2:8000`
is the host computer). On a **phone**, join the same Wi-Fi as the computer, open
**Settings** (gear icon on Home) and enter the computer's address, for example
`192.168.0.102:8000`.

## Build the APK

```bash
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
./gradlew test lint             # unit tests and Android lint
```

Release builds are signed with the key described by `keystore.properties`
(see `keystore.properties.example`). That file and the keystore are git-ignored.
Without them the release build is signed with the Android debug key, so a fresh
clone still produces an installable APK.

## Layout

```
app/src/main/java/com/nakshatra/heritage/
├── NakshatraApp.kt        process-wide objects: settings, repository, socket, voice engine
├── MainActivity.kt        connects while on screen, disconnects when stopped
├── AppViewModel.kt
├── data/
│   ├── Models.kt          the JSON of /api/kb, /api/ask, /api/health and /ws/ui
│   ├── Network.kt         Retrofit API, WebSocket with reconnect, URL helpers
│   ├── Repository.kt      knowledge base + last-good copy on disk, settings
│   └── Constellation.kt   map projection and spanning tree (port of map.js)
├── voice/
│   ├── VoiceEngine.kt     the six-state interaction machine (port of voice.js)
│   └── AndroidSpeaker.kt  spoken answers
└── ui/
    ├── NakshatraRoot.kt   navigation, bottom bar / rail
    ├── theme/             palette and type of the website
    ├── art/               the 22 line-art motifs and the voice orb
    ├── components/
    └── screens/           Home, Explore, Category, Topic, Ask, Map, Archive, Settings
```

`ui/art/MotifData.kt` is generated from the website's `frontend/js/art.js`:

```bash
node scripts/export_android_motifs.mjs
```

## Tests

`./gradlew test` runs 23 JVM tests: the app's models against the real
`knowledge_base/heritage.json`, the REST client against a mock server, the map
geometry, and every transition of the voice state machine.
