# Bus Radar

![Build](https://github.com/gertelyoav/bus-radar/actions/workflows/build.yml/badge.svg)

An Android app that shows, on a live map, every bus currently within a chosen radius of you in Israel.

## What it does

- Live map (MapLibre + OpenStreetMap) centred on you, with a ring for the search radius (0.5 to 5 km, remembered)
- Every bus inside the ring, with its line number and an arrow for its heading, refreshed every 30 s while the app is open
- Colour shows how fresh each position is: green under 2 min, yellow 2 to 3 min, red older
- Tap a bus for its line, destination, operator and the exact time (to the second) of its last position
- The tapped bus's route is drawn with its stops; one button shows every bus on that line, anywhere
- Filter the map to the lines you care about
- Buses glide to their new positions on each refresh

## Roadmap

| Version | What it adds |
|---|---|
| 1, prototype | Map, my location, a dot per bus within 1 km, manual refresh |
| 2, usable | Line number and heading arrow on each bus, auto refresh every 30 s, details on tap |
| 3, polished | User-adjustable radius, filter by line, smooth marker movement, stale buses shown faded |
| 4, optional | Favourite lines, arrival estimates, own backend on the official SIRI feed |

## Tech

- Kotlin, Jetpack Compose, minSdk 26
- Map: [MapLibre](https://maplibre.org/) with [OpenFreeMap](https://openfreemap.org/) tiles (no API key, no billing)
- Location: FusedLocationProvider
- Data: [Open Bus Stride API](https://open-bus-stride-api.hasadna.org.il/docs) by [Hasadna](https://www.hasadna.org.il/), built on Israel Ministry of Transport SIRI data
- CI: GitHub Actions runs unit tests and builds the APK on every push

## Install on a phone

Open [the latest release](https://github.com/gertelyoav/bus-radar/releases/tag/latest) on your Android phone, download `bus-radar.apk` and open it. Android will ask you to allow installs from your browser once.

## Build locally

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. The project uses a committed debug key (`app/debug.keystore`) so every build installs over the previous one; it is not a secret.

## Credits

Bus data: Open Bus Stride, Hasadna. Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.

## License

MIT
