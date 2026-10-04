# Android security audit

Performed on 2026-10-04 against the existing backend and the Android app
(`com.nakshatra.heritage` 1.0.0), before the app was connected and again on the
final release APK.

## Summary

| Area | Result |
|---|---|
| Secrets in the repository | None exist; none found |
| Secrets in the APK | None found (unpacked and scanned) |
| Credentials needed by the app | None — the API takes no key |
| Signing material | Generated locally, git-ignored, not in the APK |
| Permissions | `INTERNET` only |
| Transport | Plain HTTP on the local network — an accepted, documented limitation |
| Backend changes | One hardening change (bounded question length); nothing weakened |

## 1. What the backend is

The website's backend is `backend/cloud_server.py`: a FastAPI process on the
demo laptop with the knowledge base in a JSON file. There is no Supabase or
Firebase project, no database, no storage bucket, no row-level security and no
authentication, so there are no service-role keys, database passwords, OAuth
secrets or API tokens anywhere in the project. The tree contains no `.env` file.

## 2. What the app is allowed to hold

| Item | In the APK? | Why it is safe |
|---|---|---|
| Default server address `http://10.0.2.2:8000` | Yes | The emulator's alias for the host computer; not a secret, not routable |
| API keys / tokens | No | The API has none |
| Signing keystore or its password | No | Read by Gradle at build time only; verified absent from the APK |
| Team names, project description | Yes | Already public in the README |

The server address a user types is stored in the app's private
`SharedPreferences`; the last reply of `/api/kb` is cached in the app's private
files directory. Neither is sensitive. Backup is disabled (`allowBackup="false"`).

## 3. Findings

### 3.1 Plain HTTP on the local network — accepted

The server speaks HTTP and WebSocket without TLS, and its address is only known
at run time (a LAN address, or `10.0.2.2` on the emulator), so cleartext cannot
be limited to a fixed domain list. `res/xml/network_security_config.xml`
therefore permits cleartext for the app. Consequences and mitigations:

* Someone on the same network could read or alter the heritage text in transit.
  The app sends no credentials and no personal data; the only upload is the
  text of a question.
* User-installed certificate authorities are not trusted; an `https://` server
  address is supported and verified against the system store.
* Android lint reports this as `InsecureBaseConfiguration`; it is the one
  security warning in the lint report and is intentional.

Removing it means putting the server behind HTTPS, which is listed under future scope.

### 3.2 The server has no authentication — pre-existing, by design

`/api/*` and `/ws/ui` are open to anyone who can reach port 8000, as they are
for the website. `/ws/edge` accepts audio from any client. This is appropriate
for a kiosk on a trusted network and is unchanged; the README now states that
the server must not be exposed to the internet. The Android app adds no new
exposure: it uses the read-only endpoints the website already uses.

### 3.3 Unbounded question length — fixed

`POST /api/ask` accepted a question of any length, which would let a client tie
up the fuzzy matcher with a very large body. `AskRequest.question` is now
limited to 500 characters (the website and the app cap input at 200); longer
requests receive HTTP 422. Verified: a normal question returns 200, a
600-character one 422, and the 468-case answer-engine regression still passes.

### 3.4 Data read from the server is treated as data

Topic text is rendered as plain text (Compose `Text`), never as HTML. A topic's
optional `image` is loaded only from `<server>/assets/heritage/<file>`. JSON is
decoded with unknown fields ignored (covered by unit tests), and a malformed
WebSocket frame is dropped rather than crashing the app.

### 3.5 App surface

* One permission: `android.permission.INTERNET`. (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  in the merged manifest is AndroidX's own signature-level permission.)
* One exported component: the launcher activity. No services, receivers,
  content providers or deep links of the app's own.
* The release build is not debuggable and is shrunk and obfuscated with R8.
* The WebSocket is opened while the app is on screen and closed when it stops.

### 3.6 Signing

A release keystore was generated locally for this build
(`android/keystore/nakshatra-release.jks`, with a random password in
`android/keystore.properties`). Both paths are git-ignored and are not in the
repository or the APK. **Keep a private backup of those two files**: updates to
an installed app must be signed with the same key. A clone without them builds a
release APK signed with the Android debug key.

## 4. Repository scan

Before the first commit the working tree and the staged files were scanned for
private keys, cloud and API keys, tokens, JWTs, passwords and `.env` files; the
history was scanned after committing. The result is recorded in the final
report of this work. `.gitignore` excludes `.env*` (except `.env.example`),
`*.jks`, `*.keystore`, `keystore.properties`, `*.pem`, `*.p12`, build output and APKs.

## 5. Not weakened

No access rule, validation or check in the existing backend was relaxed to make
the Android app work. The app needed no backend change at all to function.
