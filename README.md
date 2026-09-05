# SteamBridge-XR

## What it does

1. Logs into Steam with **username + password** or a **QR code** (mobile-approve), fully on-device.
   No Steam client is present on Quest, so this speaks the CM wire protocol directly:
   `IAuthenticationService` → `refresh_token` → `CMsgClientLogon` → `CMsgClientRequestEncryptedAppTicket`.
2. Caches the resulting `EncryptedAppTicket` bytes (90s TTL, ≥60s re-mint floor — Steam rate-limits
   `RequestEncryptedAppTicket` to ~1/min).
3. Serves them from a loopback TCP server on `127.0.0.1:48010` in the exact wire format the existing
   `steamshim.cpp` `relay_fetch()` already parses:
   ```
   STEAMID:<decimal>\n
   NAME:<persona>\n
   TICKET:<lowercase hex>\n
   ```
4. `steamshim` hands the ticket to the game at `ISteamUser` vt[21]/vt[22]; `eosshim` forwards it as
   EOS `STEAM_APP_TICKET(1)`. Nothing on the game side changes — you only point `relay.txt` at loopback.

## Modules

| module     | role |
|------------|------|
| `app`      | Compose UI (creds / QR / guard) + the foreground loopback server service |
| `steamcm`  | the CM protocol client: framing, crypto, auth, logon, ticket — no NDK, pure Kotlin/JVM |
| `secure`   | `TokenStore` — EncryptedSharedPreferences over an Android Keystore key |
| `delivery` | `WireFormat`, `TicketCache`, `LoopbackTicketServer` — the byte contract with `steamshim` |

The Steam wire messages are hand-rolled against pinned field numbers from
[SteamDatabase/Protobufs](https://github.com/SteamDatabase/Protobufs) (`steammessages_auth.steamclient.proto`,
`steammessages_clientserver_login.proto`, `steammessages_clientserver_2.proto`) — no generated-proto
build step, nothing to resync but the constants in `msg/AuthMessages.kt` and `msg/ClientMessages.kt`.

## Build

Needs JDK 17 + the Android SDK (compileSdk 34). First checkout, generate the wrapper:

```bash
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

Or open the folder in Android Studio and Run. Output: `app/build/outputs/apk/debug/app-debug.apk`
(arm64-v8a only).

## Installing

1. Sideload the APK (SideQuest or adb):
   ```bash
   adb install -r app-debug.apk
   ```
2. Point Pavlov's relay at the on-device server and arm eosshim, **once**, via adb (scoped storage
   blocks a second app from writing here at runtime):
   ```bash
   adb shell "echo 127.0.0.1:48010 > /sdcard/Android/data/com.vankrupt.pavlov/files/relay.txt"
   adb shell "echo 1 > /sdcard/Android/data/com.vankrupt.pavlov/files/steamlogin.txt"
   ```
3. Launch **SteamBridge-XR**, sign in (password or QR). On success it starts the relay service
   (persistent notification "Ticket relay on 127.0.0.1:48010").
4. Launch Pavlov. `steamshim` connects to loopback, pulls the ticket, EOS accepts — join an official
   lobby.

Repeat logins reuse the stored `refresh_token` + `guard_data` (machine token), so no password and no
2FA prompt after the first sign-in.

# Notes
- This uses your **real** Steam account, there is a chance of being banned from using this as its a thirdparty app.