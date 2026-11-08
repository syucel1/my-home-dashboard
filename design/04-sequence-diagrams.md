# 04: Sequence Diagrams

These are the key runtime flows. Participant names match the components in [01 Architecture Overview](01-architecture-overview.md#5-component-architecture-backend).

| # | Flow | Shows |
|---|------|-------|
| [SD-01](#sd-01-hub-start-up-and-driver-loading) | Hub start-up & driver loading | Boot order, state recovery, supervision |
| [SD-02](#sd-02-device-discovery-and-onboarding-remootio-via-mdns) | Device discovery & onboarding | mDNS discovery, credentials into the vault, registration |
| [SD-03](#sd-03-user-opens-the-main-gate-dangerous-command-with-step-up) | Open the main gate | Step-up confirmation, encrypted local command, optimistic → confirmed |
| [SD-04](#sd-04-device-pushed-event-to-notification-dehumidifier-tank-full) | Device event → notification | Push event, dedupe, channels, live UI |
| [SD-05](#sd-05-cloud-polling-under-a-rate-budget-vicare-heating) | Cloud polling with a rate budget | ViCare adaptive polling, change heating target |
| [SD-06](#sd-06-device-goes-offline-and-recovers-health-and-incidents) | Device goes offline and recovers | Heartbeats, incident, notification, auto-resolve |
| [SD-07](#sd-07-live-camera-view-webrtc-via-go2rtc) | Live camera view | WebRTC via go2rtc |
| [SD-08](#sd-08-print-a-document-from-the-dashboard) | Print from the dashboard | Upload → IPP job → status |
| [SD-09](#sd-09-sidecar-driver-roborock-start-room-cleaning) | Sidecar driver command | Roborock over MQTT Driver Protocol |
| [SD-10](#sd-10-network-scan-and-unknown-device-alert) | Network scan & unknown device | Router API + ARP + mDNS merge |
| [SD-11](#sd-11-fire-alarm-x-sense-local-mqtt-bridge--cloud-fallback) | Fire alarm (X-Sense) | Official local MQTT bridge, cloud fallback, CRITICAL routing |
| [SD-12](#sd-12-login-with-2fa-and-live-session) | Login with 2FA & live session | Auth, WebSocket subscription, snapshot + deltas |

---

## SD-01 Hub start-up and driver loading

```mermaid
sequenceDiagram
    autonumber
    participant OS as Docker
    participant APP as hub-app
    participant DB as PostgreSQL
    participant V as Credential Vault
    participant DM as Driver Manager
    participant D as Driver (each)
    participant MQ as Mosquitto
    participant HM as Health Monitor
    participant BUS as Event Bus

    OS->>APP: start container (depends_on: db, mqtt healthy)
    APP->>DB: Flyway migrate
    APP->>V: load master key (file / Docker secret)
    APP->>DB: load devices, rooms, attribute_current
    APP->>HM: init health = UNKNOWN for all devices<br/>(last known state shown as STALE)
    APP->>DM: start drivers
    DM->>DB: load enabled DriverInstances
    loop for each DriverInstance (in parallel, virtual threads)
        DM->>V: decrypt credentials
        DM->>D: create(ctx, config) + start()
        alt start OK
            D-->>DM: running
            D->>BUS: upsertDevice / publishState (resync)
            BUS->>HM: StateChanged → device ONLINE
        else start failed (auth, network)
            D-->>DM: DriverException
            DM->>DM: schedule restart (exp. back-off 5 s → 5 min)
            DM->>BUS: DriverStatusChanged(ERROR, reason)
        end
    end
    DM->>MQ: subscribe hub/v1/drv/+/status (sidecars)
    MQ-->>DM: retained status/devices of sidecars
    APP-->>OS: /actuator/health = UP
```

## SD-02 Device discovery and onboarding (Remootio via mDNS)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as Web UI
    participant API as REST API
    participant DS as Discovery Service
    participant DM as Driver Manager
    participant V as Credential Vault
    participant RD as Remootio Driver
    participant R as Remootio device
    participant REG as Device Registry

    DS->>DS: continuous mDNS browse (_remootio._tcp)
    DS-->>REG: candidate "Remootio 2462ab…" @192.168.1.74:8080
    REG-->>UI: (WS) DiscoveryFound
    U->>UI: "Add" on discovered gate
    UI->>API: GET /drivers/remootio/schema
    API-->>UI: JSON Schema (ip, apiSecretKey, apiAuthKey)
    U->>UI: paste API Secret + Auth key (from Remootio app)
    UI->>API: POST /integrations {driver: remootio, config, secrets}
    API->>V: encrypt secrets → secretRef
    API->>DM: create DriverInstance
    DM->>RD: start()
    RD->>R: WebSocket connect ws://192.168.1.74:8080
    RD->>R: AUTH
    R-->>RD: ENCRYPTED challenge
    RD->>R: ENCRYPTED QUERY (session key, AES-CBC + HMAC)
    R-->>RD: state: gate CLOSED, serial, version
    RD->>REG: upsertDevice(externalId = serial, caps = [Gate, Availability])
    REG-->>UI: (WS) DeviceRegistered "Main gate"
    U->>UI: assign room "Front garden", name "Main gate"
```

## SD-03 User opens the main gate (dangerous command with step-up)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as Web UI
    participant API as REST API
    participant SEC as Security
    participant CMD as Command Service
    participant DM as Driver Manager
    participant RD as Remootio Driver
    participant R as Remootio device
    participant BUS as Event Bus
    participant WS as WebSocket

    U->>UI: tap "Open gate"
    UI->>API: POST /devices/{gate}/commands {gate.open}
    API->>SEC: authorize(user, gate, open)
    SEC-->>API: allowed, requires step-up (policy: Gate.open)
    API->>CMD: submit → AWAITING_CONFIRMATION
    API-->>UI: 202 {commandId, challenge: PIN|TOTP|passkey}
    U->>UI: enter PIN / passkey
    UI->>API: POST /commands/{id}/confirm {proof}
    API->>SEC: verify proof (rate-limited, 3 attempts)
    SEC-->>CMD: confirmed
    CMD->>BUS: CommandStatusChanged(PENDING)
    CMD->>DM: dispatch(command)
    DM->>RD: execute(gate.open)
    RD->>R: ENCRYPTED {action: OPEN, actionId: n+1}
    R-->>RD: ENCRYPTED ack (actionId)
    RD-->>CMD: CommandAck(SENT)
    CMD->>BUS: CommandStatusChanged(SENT)
    BUS->>WS: → user queue
    WS-->>UI: "Opening…" (optimistic: position = OPENING, quality ESTIMATED)
    R-->>RD: event: LeftOpen / StateChange(OPEN) (sensor)
    RD->>BUS: StateChanged(gate.position = OPEN, GOOD)
    BUS->>CMD: matches expected state → CONFIRMED
    BUS->>WS: StateChanged + CommandStatusChanged(CONFIRMED)
    WS-->>UI: "Gate open ✓" (all connected users see it)
    Note over CMD: if no confirming state within 30 s → TIMEOUT,<br/>UI shows "Command sent, state not confirmed"
```

## SD-04 Device-pushed event to notification (dehumidifier tank full)

```mermaid
sequenceDiagram
    autonumber
    participant P as ProBreeze (Tuya)
    participant TD as Tuya Local Driver
    participant BUS as Event Bus
    participant SS as State Store
    participant HS as History
    participant HM as Health Monitor
    participant NE as Notification Engine
    participant DB as PostgreSQL
    participant CH as Channels (Web Push / ntfy)
    participant WS as WebSocket
    actor U as User

    P->>TD: TCP push (encrypted DPS update)<br/>{dp 19: fault = "FULL"}
    TD->>TD: decrypt, map DP 19 → dehumidifier.tankFull = true
    TD->>BUS: StateChanged(tankFull=true)
    TD->>BUS: DeviceEventOccurred(TankFull, INFO)
    par subscribers
        BUS->>SS: update current state
        BUS->>HS: append time-series
        BUS->>HM: rule "tankFull" → DEGRADED("Water tank full")
        BUS->>WS: push state to UI
    end
    HM->>BUS: HealthChanged(ONLINE → DEGRADED)
    BUS->>NE: DeviceEventOccurred(TankFull)
    NE->>DB: find open notification dedupeKey = "dehum-01:TankFull"
    alt none open
        NE->>DB: insert Notification (NEW)
        NE->>NE: routing rules (INFO → push, check quiet hours)
        NE->>CH: send "💧 Kitchen dehumidifier: tank full"
        CH-->>U: phone push
    else already open
        NE->>DB: occurrences++ (no new push)
    end
    NE->>WS: NotificationRaised → badge + inbox
    Note over P,NE: Tank emptied → tankFull=false → health ONLINE<br/>→ notification auto-RESOLVED
```

## SD-05 Cloud polling under a rate budget (ViCare heating)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as Web UI
    participant CMD as Command Service
    participant VD as ViCare Driver
    participant RB as Rate-Budget Manager
    participant ST as Driver KV Store
    participant VA as Viessmann API (cloud)
    participant BUS as Event Bus

    loop adaptive poll (≈ every 90–120 s)
        VD->>RB: tryAcquire("vicare", poll)
        alt budget available (excluding reserve)
            RB-->>VD: granted (used 812/1450 today)
            VD->>ST: get access token (refresh if expired)
            VD->>VA: GET /features/installations/{i}/gateways/{g}/devices/0/features
            alt 200 OK
                VA-->>VD: features JSON (temps, burner, modes, errors)
                VD->>VD: map features → capabilities, diff vs last
                VD->>BUS: StateChanged (only changed attrs)
            else 429 rate limited
                VD->>RB: markExhausted(until reset)
                VD->>BUS: AvailabilityChanged(degraded, "Rate-limited until 14:20")
            else 5xx / GATEWAY_OFFLINE
                VD->>BUS: AvailabilityChanged(false, "Viessmann gateway offline")
            end
        else budget low
            RB-->>VD: denied → interval stretched
        end
    end

    U->>UI: set target 21.5 °C (Comfort)
    UI->>CMD: thermostat.setTarget(21.5)
    CMD->>VD: execute
    VD->>RB: tryAcquire("vicare", command) – may use reserve
    VD->>VA: POST …/heating.circuits.0.operating.programs.comfort/commands/setTemperature
    VA-->>VD: 200 / success
    VD-->>CMD: SENT
    VD->>BUS: StateChanged(targetTemp=21.5, ESTIMATED)
    Note over VD: next poll confirms → GOOD, Command CONFIRMED<br/>(no extra API call spent on confirmation)
```

## SD-06 Device goes offline and recovers (health and incidents)

```mermaid
sequenceDiagram
    autonumber
    participant D as Driver
    participant BUS as Event Bus
    participant HM as Health Monitor
    participant DB as PostgreSQL
    participant NE as Notification Engine
    participant WS as WebSocket
    actor U as User

    D->>BUS: StateChanged (heartbeat, lastSeen=13:00)
    HM->>HM: watchdog tick every 15 s
    Note over HM: expectedRefresh = 60 s, missedThreshold = 2
    HM->>HM: 13:02:00 now − lastSeen > 120 s
    HM->>BUS: HealthChanged(ONLINE → OFFLINE, "No data for 2 min")
    HM->>DB: open Incident(OFFLINE, 13:02)
    BUS->>WS: device card turns red, "Offline · 0 min"
    BUS->>NE: HealthChanged
    NE->>NE: grace period 5 min (per device setting)
    alt still offline at 13:07
        NE->>DB: Notification(WARNING, dedupe "dev:offline")
        NE-->>U: push "Dehumidifier offline since 13:02"
    else recovered within grace
        NE->>NE: suppress (flapping protection)
    end
    D->>BUS: StateChanged (contact restored 13:20)
    BUS->>HM: lastSeen updated
    HM->>BUS: HealthChanged(OFFLINE → ONLINE)
    HM->>DB: resolve Incident (duration 18 min)
    NE->>DB: Notification → RESOLVED
    NE-->>U: (optional) "Dehumidifier back online after 18 min"
```

## SD-07 Live camera view (WebRTC via go2rtc)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as Web UI
    participant API as REST API
    participant MS as Media Service
    participant CD as ONVIF Camera Driver
    participant G as go2rtc
    participant C as IP Camera

    Note over CD,C: at onboarding: ONVIF GetProfiles → GetStreamUri<br/>stream URLs registered in go2rtc config via its API
    U->>UI: open "Front door" camera tile
    UI->>API: POST /media/cameras/{id}/session
    API->>MS: authorize + create session
    MS->>G: ensure stream "cam_front" (rtsp://… from vault)
    MS-->>UI: {signalingUrl: /go2rtc/api/webrtc?src=cam_front, token}
    UI->>G: WebRTC offer (SDP) via reverse proxy
    G->>C: RTSP DESCRIBE / SETUP / PLAY
    C-->>G: H.264 RTP
    G-->>UI: SDP answer + ICE
    G-->>UI: SRTP media (no transcoding if H.264)
    C-->>CD: ONVIF event: MotionAlarm
    CD->>MS: snapshot request
    CD-->>UI: (via bus/WS) MotionDetected + thumbnail
```

## SD-08 Print a document from the dashboard

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as Web UI
    participant API as REST API
    participant MS as Media Service
    participant PD as IPP Printer Driver
    participant P as HP Printer (IPP)
    participant BUS as Event Bus

    U->>UI: drag PDF onto "HP OfficeJet" card, choose 2 copies, duplex
    UI->>API: POST /devices/{printer}/print (multipart)
    API->>MS: store temp file (max 50 MB, PDF/JPEG/PNG)
    MS->>PD: execute(printer.print, fileRef, options)
    PD->>P: IPP Get-Printer-Attributes (document-format-supported)
    alt printer accepts PDF
        PD->>P: IPP Print-Job (application/pdf, copies=2, sides=two-sided-long-edge)
    else needs conversion
        PD->>PD: convert (PDF → PWG raster / URF via sidecar CUPS)
        PD->>P: IPP Print-Job (image/pwg-raster)
    end
    P-->>PD: job-id 431, job-state pending
    PD->>BUS: CommandStatus SENT + Printer.queueLength=1
    loop every 3 s until terminal
        PD->>P: Get-Job-Attributes(431)
        P-->>PD: processing / completed / aborted (+ reasons)
    end
    PD->>BUS: DeviceEvent JobCompleted (or JobFailed: media-empty)
    BUS-->>UI: "Printed ✓" toast
    MS->>MS: delete temp file
```

## SD-09 Sidecar driver: Roborock start room cleaning

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as Web UI
    participant CMD as Command Service
    participant BR as Sidecar Bridge
    participant MQ as Mosquitto
    participant SC as Roborock Sidecar (python-roborock)
    participant RR as Roborock vacuum
    participant BUS as Event Bus

    U->>UI: select Kitchen + Hall → "Clean"
    UI->>CMD: vacuum.cleanRooms([16,17])
    CMD->>BR: execute
    BR->>MQ: PUBLISH hub/v1/drv/rr1/dev/{duid}/cmd<br/>(responseTopic, correlationData)
    MQ->>SC: deliver
    SC->>RR: local protocol (TCP 58867) app_segment_clean [16,17]<br/>(fallback: cloud MQTT)
    RR-->>SC: ok
    SC->>MQ: PUBLISH response {ACCEPTED}
    MQ->>BR: CommandAck
    BR->>CMD: SENT
    loop status updates
        RR-->>SC: status (state=cleaning, battery, area)
        SC->>MQ: …/dev/{duid}/state
        MQ->>BR: state
        BR->>BUS: StateChanged(vacuum.status = CLEANING) → CONFIRMED
    end
    Note over SC,MQ: sidecar crash → MQTT Last Will "OFFLINE"<br/>→ Bridge marks devices OFFLINE → Docker restarts sidecar
```

## SD-10 Network scan and unknown device alert

```mermaid
sequenceDiagram
    autonumber
    participant ND as Network Driver
    participant RT as Main router API ⚠️
    participant VR as VPN router API ⚠️
    participant LAN as LAN (ARP / mDNS / SSDP)
    participant REG as Device Registry
    participant BUS as Event Bus
    participant NE as Notification Engine
    actor U as User

    loop every 60 s
        ND->>RT: get WAN status, DHCP leases, wireless clients (RSSI, band)
        ND->>VR: get tunnel status, connected peers
        ND->>LAN: read ARP table, mDNS/SSDP passive listen
        ND->>ND: merge by MAC → client list<br/>enrich: OUI vendor, hostname, mDNS name
        ND->>REG: link clients to known devices (by MAC/IP)
        ND->>BUS: StateChanged(router.clientsCount, wanUp, throughput)
        opt new MAC never seen before
            ND->>BUS: DeviceEvent NewClientJoined(mac, vendor, ip)
            BUS->>NE: WARNING "Unknown device joined: Espressif @ 192.168.1.88"
            NE-->>U: push (actions: Mark as known / Block)
        end
        opt device MAC absent but registered device online elsewhere
            ND->>BUS: correlate: e.g. "Dehumidifier offline AND not on Wi-Fi"<br/>→ enrich health reason
        end
    end
    loop every 15 min
        ND->>LAN: Wi-Fi survey (router API or hub NIC scan)
        ND->>BUS: WifiSurvey.networks updated (channel congestion hints)
    end
```

## SD-11 Fire alarm (X-Sense: local MQTT bridge + cloud fallback)

```mermaid
sequenceDiagram
    autonumber
    participant SA as X-Sense smoke alarm
    participant SB as X-Sense base station (SBS50)
    participant MQ as Mosquitto (Home Hub)
    participant MD as MQTT-Discovery Driver
    participant XS as X-Sense Sidecar (cloud fallback)
    participant XC as X-Sense cloud
    participant BUS as Event Bus
    participant NE as Notification Engine
    actor U as All users

    Note over SA,XC: Primary alarm path is independent of Home Hub:<br/>siren on the detectors + official X-Sense app push
    Note over SB,MQ: one-time setup in X-Sense app → "Connect to Home Assistant"<br/>→ broker = Home Hub Mosquitto, user "xsense-sbs50"
    SB->>MQ: homeassistant/binary_sensor/…/config (discovery, retained)
    MQ->>MD: discovery → register SmokeAlarm + Battery devices
    SA->>SB: RF alarm
    SB->>MQ: state topic: smoke = ON
    MQ->>MD: message
    MD->>BUS: StateChanged(smoke=DETECTED) + DeviceEvent SmokeDetected (CRITICAL)
    par cloud cross-check (redundant source)
        XS->>XC: poll every 60 s (or realtime push)
        XC-->>XS: alarmStatus = 1
        XS->>BUS: (via MQTT bridge) SmokeDetected — deduped by NE
    end
    BUS->>NE: SmokeDetected
    NE->>NE: CRITICAL → bypass quiet hours, all channels, all users
    NE-->>U: push + ntfy (priority max) + email
    Note over NE: Dashboard shows full-screen red banner<br/>until acknowledged by a user
    loop every 15 min
        XS->>XC: liveness + battery + last self-test
        XS->>BUS: StateChanged(battery, lastTest) → Health Monitor
    end
```

## SD-12 Login with 2FA and live session

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as Web UI
    participant API as REST API
    participant SEC as Security
    participant WS as WebSocket
    participant SS as State Store

    U->>UI: username + password
    UI->>API: POST /auth/login
    API->>SEC: verify Argon2id hash, rate-limit per IP/user
    SEC-->>UI: 200 {mfaRequired: TOTP}
    U->>UI: 6-digit code
    UI->>API: POST /auth/mfa
    SEC-->>UI: Set-Cookie session (HttpOnly, Secure, SameSite=Strict) + CSRF token
    UI->>API: GET /devices?include=state,health
    API->>SS: snapshot
    SS-->>UI: devices + current state (with version per device)
    UI->>WS: CONNECT (cookie) → SUBSCRIBE /topic/devices, /topic/notifications, /user/queue/commands
    WS-->>UI: deltas (StateChanged v+1…)
    Note over UI,WS: on reconnect: UI sends last versions →<br/>server replays missed deltas or tells UI to refetch snapshot
```
