# Competitor: BeepBeep (com.timelineco.beepbeep), v2.2.1

Looked at on 25 Nov 2026 on a Samsung S938B, fresh install, no adapter connected, nothing granted.
Screenshots in `beepbeep/`. Only the running app and the permissions Android shows were looked at; the
app itself was not copied or decompiled.

## What it is

A trip and driving app first, a car scanner second. Three tabs: **Journey** (trips), **Garage** (car,
fuel, sensors) and **You** (driving score, streaks, badges, settings). A "Mechanic mode" switch shows
every gauge and code; the default "Tripper mode" hides them.

## Features

| Area | What BeepBeep does |
|---|---|
| Trips | Record automatically when the adapter connects, with the screen off. Or tap "Start trip without adapter" (phone GPS only). "All trips": maps, charts, replay, merge, share. |
| Start without opening the app | Optional (off by default): starts when the saved adapter powers on, **when the car's Bluetooth stereo connects**, or when it detects driving. Needs location "All the time" and Physical activity. |
| Roads | "Your roads": road quality on every road driven (accelerometer + gyroscope + GPS). |
| Driving score | Smooth-driving score per trip; 85+ builds a streak; weekly challenges ("3 drives with no hard brakes earns Feather foot"); 14 collectible stamps; weekly / monthly stats. |
| Fuel | Manual fill-up prices → price chart, map of where fuel was cheaper, trip cost in ₹ (default ₹102/L). Calibrates fuel estimates from receipts after one full tank. Fuel type includes **Petrol (E20)**. |
| Vehicle | Profile by VIN with presets (engine size, tank); "health passport"; VIN, ECUs, calibration IDs. |
| Codes | 9,533 trouble codes from OBDex (CC0-licensed open database). OBD-II guide. |
| Adapters | Bluetooth Classic, BLE and Wi-Fi; auto-connect; adapter terminal and log. |
| Account | Signed out: trips on the phone only. Free sign-in: backup of 1 month of trips, 2 cars. **Pro**: 2 years of history, more vehicles, sync across devices. Launch offer: Pro free until 31 Mar 2027 if you connect and sign in. |
| Growth | Share with a friend; app tour; in-app feature requests with replies. |
| Data collection | On by default: road quality, traffic speeds per road segment, which ECUs answered, connection reports, vehicle profiles (sensors, ECUs, codes). Each says "never who you are, your VIN or location". |

## Permissions it declares

Location (fine, coarse, **background**), **foreground service: location** and **connected device**,
**companion device presence / start from background / run in background** (this is how trips start when
the adapter appears), activity recognition, Bluetooth scan and connect, local network (Wi-Fi adapters),
boot completed, notifications, Play Billing.

## GetAutoSure vs BeepBeep

| | GetAutoSure | BeepBeep |
|---|---|---|
| Plain-language fault explanation + India repair costs | ✅ strong | Generic guide |
| AI Mechanic that knows the car | ✅ | ❌ |
| Used Car Check (cleared codes, odometer, RTO record) | ✅ | ❌ |
| Dealer Saathi | ✅ | ❌ |
| Health score from live sensors | ✅ | "Health passport" |
| Fuel intelligence (per tank, alerts vs the car's own past) | ✅ | Price log + calibration |
| Automatic trip log | ⚠️ mileage per trip only | ✅ |
| Route maps, replay | ❌ | ✅ |
| Driving score, streaks, badges | ❌ | ✅ |
| Road quality | ❌ | ✅ |
| Cloud backup / sync | ❌ | ✅ (Pro) |
| Fault code database size | 731 maker-specific + generic | 9,533 (OBDex) |
| E20 fuel type | ⚠️ flags a tank that needed more fuel ("could be higher ethanol"), no E20 setting | ✅ |

## Recommendation

**Copy (fits our product):**
1. **Automatic trip log**: every drive a card with distance, time, km/l, ₹ cost and any fault that appeared. Reuses our existing connected-device foreground service, no new location permission.
2. **Companion Device Manager auto-start**: Android wakes the app when the paired adapter powers on. Cleaner than background location for "trips start by themselves".
3. **Fuel receipts calibration and E20**: small additions to FuelIntelligence; E20 is now standard petrol in India.
4. **Cloud backup of scan and trip history** once there are users.
5. **Check OBDex** (CC0, free to use) for codes our 731 maker-specific list doesn't cover.

**Maybe later:** route maps (opt-in location), a simple driving-smoothness score.

**Skip:** road quality, traffic mapping, streaks and stamps (fun, but away from car health, used cars and
dealers), default-on data sharing.
