# APK validation

| | |
|---|---|
| File | `android/app/build/outputs/apk/release/app-release.apk` (published as `nakshatra-v1.0.0.apk`) |
| Package | `com.nakshatra.heritage` |
| Version | 1.0.0 (version code 1) |
| Size | 1,786,001 bytes (1.8 MB) |
| SHA-256 | `7671f4deea81ae8e4576a763eea2bcdc72207a3ba8142cbae313083e7291425a` |
| Min / target / compile SDK | 26 (Android 8.0) / 36 / 36 |
| Build type | `release`, R8 code and resource shrinking enabled, not debuggable |
| Signature | APK Signature Scheme v2, `apksigner verify`: verifies |
| Signer | `CN=Anmol Garg, O=Team Nakshatra, C=IN`, certificate SHA-256 `98ffe3090794d5ce09e7dbd3217dd06d4fa58ed0fce27368f0c05f879f226c43` |
| Permissions | `android.permission.INTERNET` |

**Signing status.** The APK is signed with a release key generated for this
project on the build machine. It is a self-signed key suitable for direct
installation and for a GitHub Release; the keystore and its password are kept
out of the repository (see `reports/android_security_audit.md`).

## Environment

| | |
|---|---|
| Android Studio | 2025.3, bundled JBR 21 |
| Android Gradle Plugin / Gradle / Kotlin | 9.1.0 / 9.3.1 / 2.2.10 |
| Device | Android emulator `Pixel_7_API_29` (Android 10, arm64, 1080 × 2400) |
| Backend | `backend/cloud_server.py` on the host, reached at `http://10.0.2.2:8000`, Vosk `vosk-model-en-in-0.5`, 101 topics |

## Build checks

| Check | Command | Result |
|---|---|---|
| Unit tests | `./gradlew testDebugUnitTest` | 23 tests, 0 failures |
| Lint | `./gradlew lintDebug lintRelease` | 0 errors, 21 warnings (14 newer-dependency notices, 3 KTX style, 1 AGP version, 1 obsolete-SDK check, 1 data-extraction rules, 1 cleartext configuration — intentional) |
| Release build | `./gradlew assembleRelease` | Successful |

## Run checks on the emulator (release APK)

| Check | How | Result |
|---|---|---|
| Install | `adb install app-release.apk` on a clean device | Success |
| Launch | `am start -W` | Status ok; start-up measured between 0.4 s and 3.3 s |
| Backend connectivity | Server's `/api/health` while the app is open | `ui_clients: 1` — the app's WebSocket is connected |
| Real data | Home counts | 101 topics · 363 ways to ask · 15 themes · 78 places, matching `/api/health` |
| Navigation | All five tabs, theme → topic → map, Settings, system Back | Correct; back stack returns to the previous screen |
| Typed question | "Hampi", "Holi", "Konark", "Bharatanatyam" on the Ask tab | Processing → Answering, correct topic, match time and layer shown |
| No match | A fragment the engine cannot place | "That is not in my archive yet", with suggestions |
| Live voice pipeline | A spoken "Why do we celebrate Diwali in India" replayed through the real edge protocol (`backend/test_edge_sim.py`) | Edge connected → Nakshatra detected → Listening with live waveform and partial transcript → Processing → Diwali answer; wake confidence and wake-to-answer time shown |
| Map | Open, tap a star, open a topic's "Show on map" | Star selected, card updated; the requested place is pre-selected |
| Archive search | "silk" | Banarasi and Kanchipuram silk, expanded automatically |
| Topic detail | Open from Home, Explore and Archive | Answer, place, era, ways to ask, aliases, related topics |
| Server stopped, archive cached | Stop the server, pull to refresh | Banner "Could not reach the Nakshatra server…", saved archive still browsable; Settings reports the failure |
| Server stopped, first run | Clear app data, launch | "The archive is out of reach" with Try again and Server settings |
| Recovery | Restart the server, Try again | Archive loads; WebSocket reconnects by itself |
| Rotation | Portrait → landscape → portrait | No restart of state; landscape uses a navigation rail and a side-by-side map |
| Restart | Force-stop and relaunch | Theme and server address kept |
| Themes | Dark and light | Both readable; status-bar icons follow the theme |
| Crashes | `logcat` `AndroidRuntime` during all of the above | 0 fatal exceptions, 0 ANRs |

Not exercised: a physical phone (none was connected), spoken answers by ear
(the emulator's speech output was not listened to; the engine initialises and
the "Read aloud" control is present), a real ESP32 (the edge protocol was driven
by the project's own simulator), Android versions other than 10, and tablets
(the landscape layout was checked on the phone emulator only).

## Images

Topics in the current knowledge base carry no photographs, so no remote image
was available to load; cards and headers use the line art, as on the website.
The code path for a topic `image` (Coil, `<server>/assets/heritage/…`) is in
place but untested against real data.

## Embedded-secret inspection

The release APK was unzipped (92 files) and every file scanned as raw bytes.

| Pattern | Matches |
|---|---|
| Private-key blocks (`BEGIN … PRIVATE KEY`) | 0 |
| AWS access key ids, Google API keys, `sk-` keys, GitHub tokens | 0 |
| JSON Web Tokens, bearer tokens | 0 |
| `service_role`, Supabase or Firebase identifiers | 0 |
| Password or API-key assignments | 0 |
| Keystore files or `keystore.properties` | 0 |
| The release keystore's password (exact value) | 0 |

URLs present in the APK: the default server address `http://10.0.2.2:8000`,
and library strings (Android XML namespace, issue-tracker and licence links from
Compose, Kotlin and OkHttp). No other host is contacted.
