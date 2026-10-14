---
name: mobile-network
description: Testing mobile apps under bad or no connectivity — offline mode, network switching, slow/lossy networks, airplane mode, background sync and conflict handling — with concrete commands for Android emulators/devices (adb, emulator console, Appium mobile:setConnectivity) and iOS (simulator limits, Network Link Conditioner, proxy throttling). Use for offline-first apps, field-worker apps, sync features, "what happens when the network drops", or flaky failures that depend on connectivity.
---

# Mobile network & offline testing

## 1. Model first

Write the sync model into `domain/states/sync.md` (from AC, devs, observation): which actions work offline, where data is queued, when/how it syncs, what wins on conflict, what the user sees (banners, badges, retry), what happens after app kill / reboot / logout while unsynced.
Each unclear point → `> [!question]`. Offline behaviour is usually under-specified — that is the main finding.

## 2. Test ideas (state transitions + error guessing)

| Situation | Check |
|---|---|
| Start offline (cold start, no cache / with cache) | app usable? correct offline indication? no crash on login/token refresh |
| Go offline mid-action (during save, upload, form submit) | no data loss, no duplicate on reconnect, clear status |
| Queue several changes offline → reconnect | order preserved, all synced once, UI updates, server state correct |
| Conflict: same record changed on server/other device | rule from domain applied, user informed |
| Switch Wi-Fi ↔ mobile data, VPN on/off | requests resumed, no stuck spinners |
| Slow / lossy network (2G/EDGE, 30 % loss, high latency) | timeouts, retries with backoff, cancel works, no frozen UI |
| Captive portal / DNS failure / server 5xx | treated like offline, not as "wrong password" |
| App killed or device rebooted while unsynced | queue survives, sync resumes |
| Long offline period (token expiry, stale reference data) | re-auth without losing queued data |
| Large attachments (photos) on a weak network | progress, resume, size limits |

## 3. Android

| Control | Command |
|---|---|
| Wi-Fi / mobile data | `adb shell svc wifi disable` · `adb shell svc data disable` (and `enable`) |
| Airplane mode (Android 11+) | `adb shell cmd connectivity airplane-mode enable` / `disable` |
| Appium (UiAutomator2) | `driver.executeScript("mobile: setConnectivity", mapOf("wifi" to false, "data" to false, "airplaneMode" to false))` |
| Emulator speed / latency | `adb emu network speed edge` (gsm, hscsd, gprs, edge, umts, hsdpa, lte, full) · `adb emu network delay umts` (gprs, edge, umts, none) |
| Packet loss / custom conditions | emulator Extended Controls → Cellular; or a throttling proxy (Charles / Proxyman / mitmproxy) |
| Background / kill | `adb shell input keyevent KEYCODE_HOME` · `adb shell am force-stop <package>` · `adb shell am kill <package>` |
| Doze (deferred sync) | `adb shell dumpsys deviceidle force-idle` · `adb shell dumpsys deviceidle unforce` |

Always restore connectivity in teardown (`svc wifi enable; svc data enable; airplane-mode disable`) — a test that leaves the device offline breaks the next test.

## 4. iOS

- **Simulator** shares the Mac's network: no per-simulator toggle. Use the macOS **Network Link Conditioner** (Xcode → Additional Tools) or a throttling proxy; "offline" = block the backend host in the proxy rather than cutting the Mac's network.
- **Real device**: Settings → Developer → Network Link Conditioner (profiles: 100 % loss, 3G, Edge, Very Bad Network); airplane mode via Control Center (manual) — automate via proxy blocking instead.
- Background/foreground with Appium XCUITest: `mobile: backgroundApp` (`seconds`), `terminateApp` / `activateApp`.

## 5. Automation tips

- Put connectivity control in one helper (`NetworkControl.offline()/online()/slow()`) per platform; tests stay readable.
- Assert on app state and on the **server** (API) after sync — not only on the UI badge.
- Waits: wait for the sync indicator / server state with explicit timeouts; never sleep.
- Tag these tests (`@network`, `offline`) — they are slower and need a dedicated device; don't run them in parallel on one device.
- Cloud farms: check the provider's network-throttling support (e.g. Sauce Labs network conditioning for real devices) before planning automation there.
