# 05: Integration Specifications

There is one section per device family. Each one records: **transport · auth · local/cloud · driver type · capabilities · health signals · risks · open questions**.

Legend:
- 🟢 local and documented
- 🟡 local but reverse-engineered, or cloud and official
- 🔴 cloud and unofficial
- ⚠️ needs input from Serkan

## Summary

| Device | Rating | Transport | Driver | Phase |
|--------|--------|-----------|--------|-------|
| Remootio gate opener | 🟢 | Local WebSocket API v2 (encrypted), mDNS discovery | In-process `remootio` | 12 |
| HP & other printers | 🟢 | IPP / IPP Everywhere (+ CUPS sidecar for legacy) | In-process `ipp` | 13 |
| Routers, VPN router, network clients, Wi-Fi survey | 🟢/🟡 ⚠️ | Vendor API + SNMP + ARP/mDNS/SSDP | In-process `network` (+ vendor adapters) | 14 |
| X-Sense fire alarms (SBS50) | 🟡 | **Official local MQTT bridge** (primary); cloud API via python-xsense (fallback) | In-process `mqtt-discovery` + optional sidecar `xsense` | 15 / 19 |
| Viessmann ViCare heating | 🟡 | Official cloud REST API (OAuth 2 PKCE), rate-limited | In-process `vicare` | 16 |
| ProBreeze dehumidifier | 🟡 ⚠️ | Tuya local (TCP 6668, AES) **or** Midea local (model-dependent) | In-process `tuya-local` (+ `midea-local` if needed) | 17 |
| Roborock vacuum | 🟡 | Local TCP 58867 (encrypted with local key); cloud MQTT fallback | Sidecar `roborock` | 18 |
| IP cameras | 🟢 ⚠️ | ONVIF (discovery, PTZ, events) + RTSP → go2rtc → WebRTC | In-process `onvif` + go2rtc | 19 |
| Fridge | ⚠️ | Brand-dependent (Home Connect / SmartThings / ThinQ) | In-process, vendor-specific | 20 |
| Coffee machine | ⚠️ | Brand-dependent (Home Connect / De'Longhi / Melitta / …) | In-process, vendor-specific | 20 |

---

## 1. Remootio: main gate 🟢

| Item | Spec |
|------|------|
| Transport | WebSocket server on the device, `ws://<ip>:8080`, **API v2**. JSON frames; actions and state go in `ENCRYPTED` frames (AES-CBC + HMAC-SHA256, session key from the AUTH challenge) |
| Discovery | mDNS service `_remootio._tcp`; instance name = serial number |
| Auth | `API Secret Key` + `API Auth Key` (64-char hex each), shown in the Remootio app after enabling **Websocket API** (needs the master key) |
| Keep-alive | Send `PING` well under the device's 120 s no-traffic timeout (we use 30 s) |
| Capabilities | `Gate` (position from the gate status sensor, if fitted), `Availability` |
| Commands | `TRIGGER`, `OPEN`, `CLOSE` (OPEN/CLOSE need a status sensor), `QUERY`, `RESTART` (admin) |
| Events | `StateChange`, `RelayTrigger`, `LeftOpen`, `Connected`, … → mapped to `Gate.Opened/Closed/LeftOpen` + actor |
| Health | Socket connected and PONGs received → ONLINE; socket closed → OFFLINE immediately; reconnect back-off 2 s → 60 s |
| Security | **Step-up confirmation** for `open`/`trigger`. The audit log records who opened the gate and when. The "auto-close after N min" automation is opt-in |
| Implementation | Native Java: JDK `HttpClient` WebSocket + `javax.crypto`. About 600 LOC. Contract-tested against recorded frames |
| Risks | Without a gate status sensor, the position is `UNKNOWN` and commands can only be `TRIGGER` (shown as "pulse") |
| ⚠️ Question | Is the Remootio fitted with a **gate status sensor**? Is it Remootio 1, 2 or 3? |

## 2. Viessmann ViCare: heating 🟡

| Item | Spec |
|------|------|
| Transport | HTTPS REST: `https://api.viessmann.com/iot/v2/…` (features API) |
| Auth | OAuth 2.0 **Authorization Code + PKCE**. Client ID from the **Viessmann Developer Portal**, created with your ViCare login. Redirect URI → `https://<hub>/oauth/vicare/callback`. Refresh token kept in the vault |
| Topology | `installations → gateways → devices → features` |
| Capabilities | `Thermostat` (per heating circuit), `WaterHeater` (DHW), `BoilerDiagnostics`, `TemperatureSensor` (outside temperature) |
| Commands | Set the program temperature (normal/comfort/reduced), set the operating mode, DHW target, one-time DHW charge |
| Rate limits | **Free tier: 120 calls per 10 min and 1,450 calls per 24 h. Exceeding either blocks the account for 24 hours.** Paid tiers raise this; TRVs and climate sensors need the "Advanced" tier |
| Polling | One full-features call per device every **≈ 90–120 s** (≈ 720–960 calls a day), with **150 calls a day reserved** for user commands. Adaptive (see [03 §6](03-driver-sdk-and-messaging.md#6-rate-budget-manager)) |
| Health | `GATEWAY_OFFLINE` error → device OFFLINE with reason "Viessmann gateway offline"; HTTP 429 → DEGRADED "Rate-limited until …"; hub internet down → CLOUD_UNREACHABLE |
| Events | Derived by diffing: burner on/off, mode change, new error code → `BoilerDiagnostics.Fault` |
| Risks | The cloud is the only path, quotas are strict, and the API is known to lose contact with the gateway at times. The ViCare phone app does **not** count against our quota, but any other script on the same account would |
| ⚠️ Question | Boiler model? Number of heating circuits? Any ViCare TRVs or room sensors? (Those would need the paid tier.) Stay on the free tier? |

## 3. X-Sense: fire / CO alarms 🟡

**Primary path (local, official): the X-Sense "Connect to Home Assistant" MQTT bridge.**
Since app v1.34 and a matching SBS50 firmware, the **SBS50 base station can publish alarm, battery, device-status and signal data directly to a local MQTT broker**. Home Hub runs its own Mosquitto, so we point the SBS50 at it.

| Item | Spec |
|------|------|
| Transport | MQTT, from the SBS50 to **our Mosquitto**. Configured once in the X-Sense app: Settings → *Connect to Home Assistant* → broker IP, port, username/password |
| Message format | Home Assistant **MQTT Discovery** convention (`homeassistant/<component>/<id>/config` + state topics) |
| Driver | In-process **`mqtt-discovery`**: a *generic* driver that consumes HA-style discovery topics and maps `binary_sensor` (device_class smoke/carbon_monoxide/battery) and `sensor` (battery %, signal) onto Home Hub capabilities. **This driver is reusable**: Zigbee2MQTT, Tasmota, ESPHome (MQTT), Shelly and many others publish the same format |
| Broker security | A dedicated MQTT user `xsense-sbs50`, with an ACL allowing only `homeassistant/#` and the X-Sense state topics |
| Capabilities | `SmokeAlarm`, `Battery`, `Availability` (+ `TemperatureSensor`/`HumiditySensor` for X-Sense thermo-hygrometers) |
| Health | Community reports say the bridge sends little or no periodic heartbeat. We therefore treat "no message" as **normal**, track the base station's MQTT connection (Last Will, if published), and use the **cloud fallback for daily liveness and battery checks** |
| Risks | Community reports of entities not updating during real smoke tests and of devices dropping until re-linked. **Must be verified in phase 15 with a real smoke/test-button event** before relying on it |

**Fallback / enrichment path (cloud, unofficial): `xsense` sidecar using `python-xsense`**

| Item | Spec |
|------|------|
| Transport | X-Sense cloud REST (`api.x-sense-iot.com`, JWT/Cognito auth) and, where possible, cloud MQTT |
| Use | Liveness every 15 min, battery levels, last self-test date, and as a second source for alarms (polled every 60 s) |
| Risk | Unofficial, so it can break at any time. Use a **separate X-Sense account** shared from the main one, so the main app session is not logged out |

**Safety rule ([ADR-007](08-decisions-and-open-questions.md))**: the hub is a *secondary* alerting path only. The detectors' own sirens and the official X-Sense app remain primary.

⚠️ Questions: Which detector models (smoke only, or combined smoke + CO)? How many? Is everything on the SBS50 base station?

## 4. ProBreeze dehumidifier 🟡 ⚠️

ProBreeze sells Wi-Fi models on **different white-label platforms**:
- Many use **Tuya** (paired with the *Smart Life* / *Tuya Smart* app). Example: the PB-D-18W-WF OmniDry 12L.
- Some older units pair with **NetHome Plus**, which suggests **Midea** firmware.
- Newer units use the "Pro Breeze" app, platform to be confirmed.

**Driver plan:** `tuya-local` is the main driver. A `midea-local` variant is added only if the model needs it.

| Item | Tuya local spec |
|------|-----------------|
| Transport | TCP **6668**. Protocol versions 3.3/3.4/3.5 (AES-ECB / AES-GCM, with session-key negotiation from 3.4). UDP 6666/6667 broadcasts for discovery |
| Auth | Per-device **local key**. Obtained **once** by linking the Smart Life account to a free **Tuya IoT Developer Platform** project and reading the device list (devId + localKey). The hub's onboarding wizard guides this |
| Mapping | Tuya **DPs** (data points) → capabilities through a **device profile file** (YAML), e.g. `dp1 → switch.on`, `dp2 → dehumidifier.targetHumidity`, `dp3 → humidity`, `dp4 → fanSpeed`, `dp5 → mode`, `dp19 → faultCode (bitmap: FULL, E1, …)` |
| Capabilities | `Switch`, `Dehumidifier`, `HumiditySensor`, `TemperatureSensor` (if present), `Consumables` (filter life, if present) |
| Health | Push over a persistent socket + heartbeat every 10 s; socket loss → OFFLINE. `tankFull` / fault codes → DEGRADED with a human-readable reason |
| Bonus | The same driver unlocks **any Tuya device** (plugs, bulbs, sensors) through profile files only, with no code |
| Risks | The local key changes if the device is re-paired. Some Tuya firmware is moving to newer protocol versions |
| ⚠️ Question | Exact model (e.g. PB-D-18W-WF, PB-20) and **which app it is paired with today** (Smart Life / Pro Breeze / NetHome Plus)? |

## 5. Roborock: robot vacuum 🟡

| Item | Spec |
|------|------|
| Transport | **Local TCP 58867**. Messages are encrypted with the device's **local key** (protocol 1.0 / L01 / A01 / B01 depending on the model). Cloud MQTT is the fallback |
| Auth | The cloud login is used **once** (email + code) to fetch `duid`, `localKey`, the room map and the home data. After that, normal control is local |
| Driver | **Sidecar `roborock`** built on **`python-roborock`**, the library behind the Home Assistant integration. It is actively maintained and tracks Roborock's protocol changes, which is the reason for using a sidecar ([ADR-004](08-decisions-and-open-questions.md)) |
| Capabilities | `Vacuum` (start/pause/dock/locate/clean rooms/fan/mop mode), `Battery`, `Consumables` (main brush, side brush, filter, sensors), `Availability`; later a `Map` (image) |
| Events | `Started`, `Finished(area, duration)`, `Error(code → text)`, `Stuck`, `DustBinFull` (dock models) |
| Health | Local socket state + status poll every ≈ 30 s; error codes → DEGRADED |
| Risks | Newer models (e.g. some Q-series) don't support local mode. Maps and routines need the cloud. Give the vacuum a **static IP / DHCP reservation** |
| ⚠️ Question | Exact model (e.g. S8 MaxV Ultra, Qrevo …)? |

## 6. Printers (HP and others) 🟢

| Item | Spec |
|------|------|
| Discovery | mDNS `_ipp._tcp`, `_ipps._tcp` (AirPrint/IPP Everywhere); SNMP fallback |
| Transport | **IPP 2.0** over HTTP(S) on port 631: `Get-Printer-Attributes`, `Print-Job`, `Get-Jobs`, `Get-Job-Attributes`, `Cancel-Job` |
| Status | `printer-state`, `printer-state-reasons` (media-empty, toner-low, door-open, media-jam), `marker-levels` (ink %), `queued-job-count` |
| Printing | PDF directly if `document-format-supported` includes `application/pdf` (most modern HP printers do). Otherwise convert to PWG raster / URF through the **CUPS sidecar** (`cups-filters`) |
| UI | Drag and drop onto the printer card. Options: copies, duplex, colour/mono, paper size, page range. Job progress is shown live |
| Capabilities | `Printer`, `Consumables` (ink/toner), `Availability` |
| Health | Polled every 60 s while idle and every 3 s while a job is active; `printer-state-reasons` → DEGRADED (paper out, low ink) |
| Risks | Printers that sleep may respond slowly, so the first poll after wake-up gets a longer timeout. Very old printers that only support PCL need CUPS |
| ⚠️ Question | Printer models? |

## 7. IP cameras 🟢 ⚠️

| Item | Spec |
|------|------|
| Discovery | **ONVIF WS-Discovery** (UDP 3702 multicast) |
| Control | ONVIF SOAP: `GetProfiles`, `GetStreamUri`, `GetSnapshotUri`, PTZ (`ContinuousMove`), events (`PullPoint` subscription → motion/person) |
| Video | RTSP stream URLs registered in **go2rtc** (through its REST API). The browser uses **WebRTC** (low latency) with an HLS/MSE fallback. No transcoding when the camera sends H.264. H.265 needs transcoding or a browser that supports it |
| Capabilities | `Camera` (live, snapshot, PTZ), `MotionSensor` events |
| Recording | **Out of scope for v1**: use the camera's SD card or NVR. The hub stores event snapshots only (7-day retention) |
| Non-ONVIF cameras | Cloud-only brands (e.g. Ring, Arlo, Blink) may need a sidecar or have no local video. Tapo, Reolink, Hikvision, Dahua and Amcrest are usually fine over RTSP/ONVIF |
| ⚠️ Question | Camera brands and models? An NVR? |

## 8. Network: routers, VPN router, clients, Wi-Fi 🟢/🟡 ⚠️

**Generic layer (works with any router):**
- The **ARP table** and an active ping sweep of the LAN /24 (rate-limited) provide the IP ↔ MAC mapping.
- **mDNS / SSDP** passive listening provides hostnames and device types.
- An **OUI lookup** (offline IEEE database) provides the vendor.
- **SNMP v2c/v3** (where supported) provides interface counters and uptime.
- A WAN check (ping 1.1.1.1 + DNS resolve + HTTPS HEAD) every 30 s gives the hub's own "internet up" signal, which drives `CLOUD_UNREACHABLE`.

**Vendor adapters (chosen once the brands are known ⚠️):**

| Vendor | API | What it adds |
|--------|-----|--------------|
| UniFi | UniFi Network API (local, API key) | Wireless clients with RSSI/band/AP, throughput, block client |
| OpenWrt (incl. many VPN routers) | `ubus` over HTTP (rpcd) / LuCI JSON-RPC | DHCP leases, wireless assoclist, WireGuard/OpenVPN status |
| GL.iNet (popular VPN routers) | GL.iNet API (OpenWrt-based) | VPN client/server status, connected devices |
| AVM Fritz!Box | TR-064 (SOAP) | Hosts, WAN, Wi-Fi, call list |
| ASUS (Merlin) | HTTP endpoints / SSH | Clients, WAN |
| TP-Link Omada / Deco | Omada controller API / unofficial | Clients |
| ISP router | Often none → generic layer only | — |

**Capabilities:** `NetworkRouter` (WAN up, public IP, uptime, throughput, VPN tunnels), `NetworkClient` (one per discovered client, shown in a "Network" view rather than on the main dashboard), `WifiSurvey` (neighbouring SSIDs, channel, RSSI, from the router API or the hub's Wi-Fi NIC if it has one).

**Cross-correlation (a differentiator):** a registered device going OFFLINE is checked against the network. If the device is also gone from Wi-Fi, the reason becomes "Device is not connected to Wi-Fi (last seen on AP 'Upstairs', RSSI −84 dBm)". If it is still on Wi-Fi, the reason becomes "On the network but not responding".

⚠️ Questions: main router make/model; VPN router make/model; mesh APs? Is the hub on wired Ethernet?

## 9. Fridge ⚠️

| Likely brand ecosystem | Integration | Notes |
|------------------------|-------------|-------|
| Bosch / Siemens / Neff / Gaggenau | **Home Connect API** (official, OAuth, SSE event stream) | Good: official, with push events. Door alarm, temperatures, super cooling/freezing |
| Samsung | **SmartThings API** (official, PAT/OAuth) | Good |
| LG | **ThinQ Connect API** (official, PAT) | Good |
| Liebherr | SmartDevice / Home API | Limited |
| Haier / Hoover / Candy | hOn (unofficial) | Sidecar candidate |

Capabilities: `Fridge`, `Appliance`, `TemperatureSensor`. Notifications: door open > 2 min, temperature alarm.

## 10. Coffee machine ⚠️

| Likely brand | Integration |
|--------------|-------------|
| Siemens EQ / Bosch / Neff | **Home Connect API** (official): program start for some models, status, water/beans/descale alerts |
| De'Longhi (Coffee Link) | Unofficial (Ayla cloud), so a sidecar candidate |
| Melitta (Connect) | Unofficial BLE/cloud |
| Jura (J.O.E.) | BLE (unofficial), which needs a Bluetooth adapter on the host |
| Smeg / others | Often none, so a smart plug + power monitoring gives a "brewing" proxy |

Capabilities: `CoffeeMaker`, `Appliance`, `Consumables`. Remote start is shown only when the vendor allows it; Home Connect requires remote start to be enabled on the machine itself.

## 11. Generic and future integrations (post-v1, enabled by the architecture)

| Integration | Route |
|-------------|-------|
| Zigbee (lights, sensors, buttons) | Zigbee2MQTT container + USB coordinator → `mqtt-discovery` driver (no new code) |
| Matter / Thread | A Matter controller sidecar (python-matter-server) → sidecar bridge |
| Tasmota / ESPHome / Shelly | `mqtt-discovery` driver |
| Any Tuya device | `tuya-local` + profile file |
| Smart plugs with energy monitoring | `PowerMeter` capability |
| Weather / sun position | Cloud poll driver (Open-Meteo) for rules |
| Bridge from an existing Home Assistant | `homeassistant-bridge` driver over the HA WebSocket API, as a stop-gap for long-tail devices |
