# 08: Decisions & Open Questions

## Architecture Decision Records

| ADR | Decision | Status |
|-----|----------|--------|
| ADR-001 | Java 25 + Spring Boot 4 modular monolith | Accepted |
| ADR-002 | Capability-based device model as the single internal language | Accepted |
| ADR-003 | In-process event bus; MQTT only at the edge | Accepted |
| ADR-004 | Polyglot drivers: Java SPI **and** MQTT sidecars | Accepted |
| ADR-005 | Docker Compose, host networking for `hub-app` | Proposed |
| ADR-006 | Remote access only through VPN | Proposed ⚠️ |
| ADR-007 | The hub is never the primary life-safety alarm | Accepted |
| ADR-008 | PostgreSQL + TimescaleDB for everything (no separate TSDB or Redis) | Accepted |
| ADR-009 | React + TypeScript SPA delivered as a PWA | Proposed |
| ADR-010 | Generic MQTT-Discovery driver compatible with the HA convention | Accepted |
| ADR-011 | Scope: own devices first, open standards second, no HA parity | Accepted |

### ADR-001: Java 25 + Spring Boot 4 modular monolith
**Context.** A single home server, one main developer, and a need for reliability and good tooling.
**Decision.** One deployable `hub-app` (a modular monolith with Maven modules and ArchUnit boundaries), not microservices.
**Consequences.** Simple deployment and debugging, with no network calls between core services. Modules can be extracted later if ever needed. Virtual threads (JDK 21+) make thousands of device connections cheap without reactive code.

### ADR-002: Capability-based device model
**Context.** Each vendor uses different shapes and protocols.
**Decision.** Every device is a set of typed capabilities (attributes, commands, events) defined in `hub-domain`. The UI, rules and notifications work only on capabilities.
**Consequences.** New devices automatically get UI controls and history. Drivers carry the mapping complexity. Vendor-specific extras go in a `vendor.*` capability namespace.

### ADR-003: In-process event bus, MQTT at the edge
**Context.** Kafka or RabbitMQ would add operational weight for a single host.
**Decision.** A typed in-memory bus with per-device ordering. Mosquitto is used only for sidecars and devices that speak MQTT.
**Consequences.** Low latency and fewer containers. Durability comes from Postgres projections. The `EventBus` interface allows a swap later.

### ADR-004: Polyglot drivers
**Context.** The best libraries for Roborock and X-Sense are Python; Tuya and Remootio protocols are small and well documented.
**Decision.** Drivers are either Java (SPI) or sidecar containers speaking the MQTT Driver Protocol, and both look identical to the core.
**Consequences.** We reuse maintained community libraries and get process isolation for fragile reverse-engineered code. Cost: a second runtime (Python) in the stack, and the protocol must be versioned.

### ADR-005: Docker Compose with host networking
**Context.** Discovery needs multicast/broadcast; Docker bridge networking blocks it.
**Decision.** `hub-app`, `go2rtc` and `sidecar-roborock` use `network_mode: host`. Postgres and Mosquitto bind internal ports to 127.0.0.1.
**Alternative.** macvlan (gives a dedicated LAN IP but is harder to set up; the host can't talk to the container without extra config).

### ADR-006: VPN-only remote access ⚠️
**Context.** The hub controls the gate.
**Decision.** Nothing is port-forwarded. Use the existing VPN router (WireGuard) or Tailscale on the host. Push notifications still work.
**Open.** Confirm the VPN router type and whether family phones already use it.

### ADR-007: Not the primary life-safety alarm
**Decision.** Smoke and CO alerts in Home Hub are **supplementary**. The UI states this on the fire-alarm settings page. No feature may disable or replace the native alarm path.

### ADR-008: Postgres + TimescaleDB only
**Decision.** One database for relational data, time-series, sessions and the notification store. The in-memory state cache lives in the JVM. No Redis.

### ADR-009: React + TypeScript PWA
**Decision.** React 19, Vite, Tailwind, shadcn/ui, TanStack Query, STOMP over WebSocket. Installable PWA with Web Push.
**Alternatives considered.** Angular, which is familiar to Java teams but heavier; SvelteKit, which is lighter but has a smaller component ecosystem.

### ADR-010: Generic MQTT-Discovery driver
**Context.** X-Sense now offers an official local MQTT bridge in the Home Assistant discovery format, and Zigbee2MQTT, Tasmota, Shelly and others use the same format.
**Decision.** Implement a generic driver that consumes `homeassistant/…/config` discovery topics.
**Consequences.** Opens a large device ecosystem with no per-device code, and gives local X-Sense integration.

### ADR-011: Scope discipline
**Decision.** v1.0 covers the devices on Serkan's list plus generic MQTT/Tuya. Lights and Zigbee/Matter come post-v1, and only through existing open bridges.

## Open questions

| # | Question | Blocks phase | Default if no answer |
|---|----------|--------------|----------------------|
| Q1 | Main router make/model? Mesh APs? | 14 | Generic layer only (ARP/mDNS/SNMP) |
| Q2 | VPN router make/model (GL.iNet? OpenWrt? other)? | 14, ADR-006 | Treated as OpenWrt-compatible |
| Q3 | Camera brands/models? NVR? | 19 | Generic ONVIF/RTSP |
| Q4 | Fridge brand/model? | 20 | Skip until known |
| Q5 | Coffee machine brand/model? | 20 | Skip until known |
| Q6 | Roborock model? | 18 | Assume a local-capable V1 protocol model |
| Q7 | ProBreeze model and the app it is paired with? | 17 | Assume Tuya / Smart Life |
| Q8 | Remootio version, and is a gate status sensor fitted? | 12 | Assume a sensor is present |
| Q9 | Viessmann boiler model, circuits, TRVs? Stay on the free API tier? | 16 | Free tier, 1 circuit + DHW |
| Q10 | X-Sense detector models; is everything on the SBS50 base station? | 15 | Assume SBS50 with smoke-only detectors |
| Q11 | Printer models? | 13 | IPP Everywhere |
| Q12 | Docker host hardware (mini PC / NAS / Pi)? | 6 | x86-64 mini PC, 8 GB+ |
| Q13 | Users: who in the family, and are guest roles needed? | 9 | Admin + members |
| Q14 | Own a domain for TLS (DNS-01)? | 6 | Caddy internal CA |
| Q15 | Personal only, or open-source later? (affects licence/SDK polish) | 8 | Personal; Apache-2.0-ready SDK |
| Q16 | Preferred push channel: Web Push only, or also ntfy / email / Telegram? | 11 | Web Push + email |
