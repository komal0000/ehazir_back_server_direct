# eHazir Companion — Android App

A native Android companion app for the **eHazir** attendance system.  
It lets students be marked in/out manually, stores records locally (offline-first), and automatically syncs with your eHazir server.

---

## Tech Stack

| Layer | Library |
|-------|---------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Local DB | Room (SQLite) |
| Networking | Retrofit 2 + OkHttp 4 |
| Background Sync | WorkManager |
| State | ViewModel + StateFlow |
| Dependency Injection | Hilt |
| Preferences | DataStore |

---

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/ehazir/companion/
│   │       ├── EhazirApp.kt              ← Application + Hilt entry
│   │       ├── MainActivity.kt
│   │       ├── data/
│   │       │   ├── local/               ← Room DB (entity, DAO, database)
│   │       │   ├── remote/              ← Retrofit API service + models
│   │       │   ├── preferences/         ← DataStore (server URL, token, device ID)
│   │       │   └── repository/          ← Single source of truth
│   │       ├── di/AppModule.kt          ← Hilt DI + DynamicUrlInterceptor
│   │       ├── worker/SyncWorker.kt     ← WorkManager background sync
│   │       └── ui/
│   │           ├── navigation/          ← Nav graph
│   │           ├── register/            ← Registration screen + ViewModel
│   │           ├── attendance/          ← Main entry screen + ViewModel
│   │           ├── settings/            ← Settings screen + ViewModel
│   │           └── theme/               ← Material 3 dark theme
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    ├── libs.versions.toml              ← Version catalog
    └── wrapper/gradle-wrapper.properties
```

---

## How to Build & Run

### Requirements
- Android Studio Ladybug (2024.2) or newer
- JDK 17+
- Android SDK 35

### Steps

1. **Open the project** in Android Studio:
   - `File → Open → select the android/ folder`

2. **Sync Gradle** — Android Studio will prompt automatically.

3. **Run the app** — press ▶ or `Shift+F10` on a device/emulator (API 26+).

---

## First Launch Setup

1. **Register your device**:
   - Enter your eHazir server URL (e.g. `https://demo.ehazircloud.com`)
   - Enter the registration token from your eHazir admin panel
   - Tap **Register Device** → the app receives and saves `LOCAL_TOKEN` + `DEVICE_ID`

2. **Record attendance**:
   - Enter a Student ID (free text — any identifier)
   - Select Check-In / Check-Out / OT-In / OT-Out
   - Select verification mode (Fingerprint or Card)
   - Tap **Submit Attendance**

3. **Sync**:
   - The app tries to sync immediately after each submission
   - The bottom sync bar shows pending record count
   - Tap **Sync Now** to force a sync manually

---

## API Endpoints Used

| Purpose | Method | Endpoint |
|---------|--------|----------|
| Device registration | POST | `/api/device/register` |
| Push attendance | POST | `/api/device/set` |

### Sync Payload (matches `main.js`)
```json
{
  "token": "LOCAL_TOKEN",
  "device_id": "DEVICE_ID",
  "datas": [
    {
      "rfid": "studentId",
      "in_time": "HH:mm:ss",
      "out_time": "HH:mm:ss",
      "date": "YYYY-MM-DD"
    }
  ]
}
```

---

## Notes

- The app works **offline-first** — entries are saved to local SQLite and marked `isSynced=0`, then synced when online.
- Changing the server URL in **Settings** takes effect on the next API call (no restart needed — uses `DynamicUrlInterceptor`).
- The `DynamicUrlInterceptor` in `AppModule.kt` mirrors the `MAIN_DOMAIN`/`MAIN_PROTOCOL` env vars from `main.js`.
- `inOutMode` values match the ZKTeco protocol documented in `at.md`: `0=in, 1=out, 4=OT-in, 5=OT-out`.
