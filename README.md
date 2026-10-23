# Travel Planner

A trip-planning app that keeps **the whole trip in one place**: a day-by-day itinerary with time zones, bookings with QR codes and PDFs, maps, place photos, budget, checklist, and a trip history once the trip is over.

- **Local-first and offline:** no server, no login, no account. All data stays on the device.
- **Zero cost:** only free, keyless APIs (OpenStreetMap, Wikimedia, OpenFreeMap, Open-Meteo, Frankfurter).
- **Kotlin Multiplatform + Compose Multiplatform:** Android is the main platform. iOS is supported by the architecture but has not been compiled or tested yet (no Mac available).

---

## Features

| Area | What it does |
|---|---|
| **Trips** | Create, edit, duplicate and delete trips; cover photo from the gallery or from a place in the itinerary; grouped into ongoing, upcoming and past |
| **Today** | Card on the home screen with the next event and today's tickets and boarding passes (QR code in two taps) |
| **Itinerary** | Days with activities and bookings, optional times, **an IANA time zone on every item** (`14:00 (Rome, UTC+2)`), ordering by the real instant across time zones, moving items between days |
| **Weather** | Daily forecast (max, min and conditions) on each itinerary day, up to 16 days ahead, saved for offline use |
| **Map** | Interactive map above the itinerary (one day or the whole trip), plus a **saved static image** for offline viewing |
| **Planning** | Conflict warnings (overlapping times, an activity during a flight, a night without lodging, a closed place), walking distance and time between items, suggested order for the day |
| **Places** | Search with a map (suggestions while typing, plus full search), Wikimedia photo with credits, opening hours, time zone suggested from coordinates, open in the maps app |
| **Ideas** | A list of things to do without a set day, using the same categories as the itinerary (an activity or a booking type), grouped by city. Includes **nearby attractions** from Wikipedia. Each idea moves into the itinerary with one tap |
| **Bookings** | Flight, lodging, ticket, train/bus, car rental, restaurant and other, each with its own fields; attachments (image, screenshot, PDF, photo) open full screen at maximum brightness |
| **Cities** | Detected automatically from the places you search (40 km radius) or added by hand; a screen per city with a map and the filtered itinerary |
| **Expenses** | Total and per-category budget, one-off expenses, booking prices added automatically, planned vs. spent |
| **Currencies** | Expenses and booking prices can be entered in about 30 currencies and are converted to BRL with the exchange rate of the day; the original amount is kept and shown alongside |
| **Splitting costs** | Travelers per trip, "paid by" and "split among" on each expense, balances and a settle-up with as few transfers as possible |
| **Checklist** | Pre-trip items, with suggestions to get started |
| **Summary** | Past trips open in a summary with numbers, cities, photos and total spending |
| **Sharing** | The itinerary as a **self-contained HTML file** (one file, with no attachments, codes or prices), sent through the system share sheet |
| **Calendar** | Export the itinerary as an `.ics` file to import into any calendar app |
| **Backup** | Export and import a `.zip` (all trips or one), merge or replace, with a reminder when no backup has been made in a while |
| **Reminders** *(Android)* | Notifications before flights and trains, other bookings and activities, with adjustable lead times |
| **Share to the app** *(Android)* | Receive a PDF or screenshot from another app ("Share → Travel Planner") and attach it to a booking |
| **"Today" widget** *(Android)* | Today's trip, the next event and today's tickets on the home screen |

The interface is **Brazilian Portuguese only**, and totals are in **BRL**. All strings live in Compose resources, so other languages can be added later.

---

## Stack

| Layer | Choice |
|---|---|
| Language and UI | Kotlin 2.4, Compose Multiplatform 1.12, Material 3 |
| Navigation | Navigation Compose (typed routes), horizontal transitions |
| Persistence | Room KMP with bundled SQLite (`sqlite-bundled`), versioned automatic migrations |
| Dates and time zones | `kotlinx-datetime` (`LocalDateTime` + IANA time zone) |
| Networking | Ktor Client (OkHttp on Android, Darwin on iOS) |
| Images | Coil 3 (with Ktor and the FileKit integration) |
| Map | MapLibre Compose + OpenFreeMap vector style |
| Files | FileKit (pickers, camera, sharing) |
| Dependency injection | Koin |
| Serialization | `kotlinx-serialization` (backup JSON) |

**Free APIs used (no key required):**

| Service | Used for |
|---|---|
| **Photon** (komoot) | Place suggestions while typing |
| **Nominatim** (OSM) | Full search with Portuguese names, Wikidata and opening hours (at most 1 request/s, only on user action) |
| **Wikidata, Wikimedia Commons, Wikipedia** | Place photos with credits and nearby attractions |
| **OpenFreeMap** | Vector map (attribution always visible) |
| **Open-Meteo** | Daily weather forecast (credited in the itinerary) |
| **Frankfurter** | Daily exchange rates from the European Central Bank |

Every request identifies itself with the app's `User-Agent`.

---

## Code structure

Almost all code lives in `shared/src/commonMain` (package `com.jpspm.travelplanner`):

```
core/          time and time zones, money and currencies, geography, IDs
domain/        pure rules, no platform dependencies
  model/         Trip, Activity, Reservation, Place…
  agenda/        builds the day-by-day itinerary
  planning/      conflicts, walking time, suggested order
  places/        opening hours (opening_hours)
  city/          grouping by city
  budget/        planned vs. spent, cost splitting
  weather/       forecast model and which place to use for each day
  reminders/     reminder scheduling rules
  export/        itinerary HTML and calendar (.ics)
  map/           what goes into each static map image
  repository/    repository interfaces
data/          implementations: Room, files, remote APIs, backup, export, background sync
ui/            Compose screens, ViewModels, navigation and theme
platform/      expect/actual and platform interfaces
di/            Koin modules
```

- `shared/src/androidMain`: database, PDF, screen brightness, ZIP, notifications, widget, incoming shares.
- `shared/src/iosMain`: the matching `actual` implementations, not compiled yet.
- `androidApp/`: Activity, Application, manifest and icon.
- `iosApp/`: iOS entry point (Xcode).
- `shared/schemas/`: exported schema for each database version (currently up to v8).
- `design/`: the icon (`icon.svg`) and the script that generates the Android adaptive icon.

---

## Running

**Requirements:** Android Studio (or JDK 21+ and the Android SDK with platform 37). `local.properties` must point to the SDK (`sdk.dir=...`); Android Studio creates this file automatically.

| Task | Command |
|---|---|
| Build the debug APK | `./gradlew :androidApp:assembleDebug` |
| Install on a phone or emulator | `./gradlew :androidApp:installDebug` |
| Tests (rules and formats, on the JVM) | `./gradlew :shared:testAndroidHostTest` |
| Tests on the iOS simulator (Mac only) | `./gradlew :shared:iosSimulatorArm64Test` |
| Regenerate the Android icon | `python design/make_android_icon.py` |

On Windows, use `gradlew.bat`. **iOS:** open `iosApp/` in Xcode on a Mac.

**If an Android resource "disappears" from the build:** the Gradle build cache is enabled (`org.gradle.caching=true`). If a renamed or moved resource can't be found, run `./gradlew :androidApp:mergeDebugResources --rerun-tasks` and build the APK again.

---

## Tests

Tests live in `shared/src/commonTest` and cover the domain rules:

- time zones, daylight saving time and formatting;
- money, currency conversion and formatting;
- itinerary building and ordering across time zones;
- conflicts, walking time and suggested order;
- the `opening_hours` parser;
- grouping by city;
- budget and cost splitting;
- reminders;
- search, photos, weather and exchange rates (with mocked responses, no network);
- backup format and protection against malicious archives ("zip slip");
- exported HTML and calendar (checks that codes, prices and attachments never leak);
- map projection.

The ZIP code is tested in `androidHostTest`.

---

## Data and privacy

- Everything stays in the app's private storage: a SQLite database and a files folder with attachments, photos and maps. File paths are stored as relative paths.
- Nothing is sent to a server of our own. The internet is only used to look up places, photos, attractions, the map, the weather forecast and exchange rates.
- Weather forecasts and exchange rates are a local cache: they are not included in backups.
- **Backup:** a `.zip` with `manifest.json` (`schemaVersion`), `data.json` and `files/`. Newer app versions can import older backups.
- **Shared HTML and calendar files:** never include attachments, booking codes or prices. The HTML also leaves out notes and booking details.

---

## Platforms

| | Android | iOS |
|---|---|---|
| Main app | ✅ minSdk 26, tested on a device | ⚠️ code ready, never compiled |
| ZIP backup | ✅ | ❌ ZIP not implemented yet |
| Reminders, widget, incoming shares | ✅ | ❌ out of current scope |
| Connectivity detection | ✅ | ⚠️ always "online" for now |
