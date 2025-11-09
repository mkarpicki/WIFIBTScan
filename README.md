# Wi-Fi & Bluetooth Scanner

A foreground Android service that scans nearby **Wi-Fi SSIDs** and **Bluetooth devices**, filters out unwanted MAC addresses fetched from a remote API, displays results in real time, and sends each discovered device to **ThingSpeak** channels.

---

## 📜 Table of Contents
- [High Level Flow](#high-level-flow)
- [Acceptance Criteria / Features](#acceptance-criteria--features)
- [Secrets Configuration (`secrets.properties`)](#secrets-configuration-secretsproperties)
- [Filter API (GET) — Expected Format](#filter-api-get--expected-format)
- [ThingSpeak Channels — Setup](#thingspeak-channels--setup)
- [How to Run & Test](#how-to-run--test)
- [Troubleshooting Tips](#troubleshooting-tips)
- [Future Improvements](#future-improvements)
- [Business Logic Diagram](#business-logic-diagram)

---

## 🚀 High Level Flow

1. **App starts** → `ScanService` runs as a foreground service.  
2. Service calls a **filter API** to fetch MAC addresses to ignore.  
3. Every **10 seconds**, it checks the current location.  
4. If the device moved **≥ 20 meters**, a new scan begins.  
5. Both **Wi-Fi** and **Bluetooth** scans run.  
6. Results are filtered against the list of MACs from the API.  
7. Filtered lists update the UI, and each device is uploaded to **ThingSpeak**.  

---

## ✅ Acceptance Criteria / Features

| Feature                                   | Status |
|-------------------------------------------|--------|
| Foreground service scanning Wi-Fi + BT    | ✅      |
| Distance-based trigger (≥ 20 m)           | ✅      |
| Location check every 10 s                 | ✅      |
| UI clears → "Searching…" before each scan | ✅      |
| Remote filter list via HTTPS GET          | ✅      |
| Per-device upload to ThingSpeak           | ✅      |
| Timestamp in milliseconds                 | ✅      |
| Secrets via `secrets.properties`          | ✅      |
| Retrofit + Coroutines used                | ✅      |

---

## 🔐 Secrets Configuration (`secrets.properties`)

Create a `secrets.properties` file in your **project root** (never commit it).

```properties
# ThingSpeak Write Keys
THINGSPEAK_WIFI_API_KEY=ABCDEFG1234567
THINGSPEAK_BT_API_KEY=HIJKLM9876543

# Filter API (GET)
FILTER_API_URL=https://example.com/api/filter-list
FILTER_API_KEY=filter-api-key-value
````

**Explanation**

* `THINGSPEAK_WIFI_API_KEY` → Wi-Fi channel write key
* `THINGSPEAK_BT_API_KEY` → Bluetooth channel write key
* `FILTER_API_URL` → HTTPS GET endpoint returning JSON array of MACs
* `FILTER_API_KEY` → value for `x-api-key` request header

Add to `.gitignore`:

```
/secrets.properties
```

---

## 🌐 Filter API (GET) — Expected Format

**Request**

```
GET https://example.com/api/filter-list
x-api-key: filter-api-key-value
```

**Response (JSON array)**

```json
["AA:BB:CC:11:22:33", "66:77:88:99:AA:BB"]
```

If unreachable → empty list used (no filtering).

---

## 📡 ThingSpeak Channels — Setup

Create **two channels** on [ThingSpeak](https://thingspeak.com).

### Wi-Fi Channel Fields

| Field  | Meaning                   |
|--------|---------------------------|
| field1 | ssid                      |
| field2 | bssid                     |
| field3 | rssi                      |
| field4 | timestamp (ms since 1970) |
| field5 | latitude                  |
| field6 | longitude                 |

### Bluetooth Channel Fields

| Field  | Meaning                   |
|--------|---------------------------|
| field1 | name                      |
| field2 | address                   |
| field3 | rssi                      |
| field4 | timestamp (ms since 1970) |
| field5 | latitude                  |
| field6 | longitude                 |

### Example POST (Wi-Fi)

```
POST https://api.thingspeak.com/update
api_key=ABCDEFG1234567
&field1=MyWiFi
&field2=12:34:56:78:9A:BC
&field3=-55
&field4=1730498765123
&field5=52.2301
&field6=21.0109
```

---

## ▶️ How to Run & Test

1. **Create `secrets.properties`** (see above).
2. **Sync Gradle** to generate `BuildConfig`.
3. **Run on a real device** (BLE/Wi-Fi scanning disabled in emulator).
4. **Grant permissions** when prompted.
5. **Move the device ≥ 20 m** — scanning triggers.
6. **Observe Logcat** for “Found Wi-Fi/BT” entries.
7. **Check ThingSpeak** for new entries.

---

## 🧰 Troubleshooting Tips

| Problem                        | Cause / Fix                                  |
|--------------------------------|----------------------------------------------|
| `BuildConfig` unresolved       | Add `buildFeatures { buildConfig = true }`   |
| App never asks for permissions | Verify runtime check logic in `MainActivity` |
| No BT results                  | Ensure BT enabled and permission granted     |
| Empty filter list              | Verify API URL + x-api-key                   |
| ThingSpeak returns 0           | Wrong key or rate limit exceeded             |
| Slow UI updates                | Normal — 1 s delay between uploads           |

---

## 🔮 Future Improvements

* Cache filter list offline
* Retry failed ThingSpeak uploads
* Allow user to set distance/interval
* Batch upload to avoid rate limits
* Add local database for history

---

## 🧭 Business Logic Diagram

```mermaid
flowchart TD
  A[App start] --> B[Load secrets.properties]
  B --> C[Start ScanService (foreground)]
  C --> D[GET filter list (x-api-key)]
  D --> E{List loaded?}
  E -->|Yes| F[Start loop every 10 s]
  E -->|No| F
  F --> G[Get last location]
  G --> H{Moved ≥ 20 m?}
  H -->|No| F
  H -->|Yes| I[Perform scan]
  I --> J[Clear UI → "Searching…"]
  I --> K[Scan Wi-Fi]
  I --> L[Scan Bluetooth]
  K --> M1[Filter Wi-Fi (bssid)]
  L --> M2[Filter BT (address)]
  M1 & M2 --> N[Update UI]
  N --> O[POST Wi-Fi → ThingSpeak (delay 1 s)]
  N --> P[POST BT → ThingSpeak (delay 1 s)]
  O & P --> Q[Wait next loop]
```

---

## 📎 License

For educational and development use only.
Ensure compliance with local laws governing radio scanning.

---

**Author:** ChatGPT (GPT-5) + *Mariusz* :-)
**Last Updated:** 2025-11-05

```

---
