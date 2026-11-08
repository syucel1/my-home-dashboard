# my-home-dashboard: Home Hub

A self-hosted home platform with a **Java backend** and a **modern browser UI**. It brings every device in the house onto one clean dashboard: gate, heating, fire alarms, dehumidifier, robot vacuum, printers, cameras, network, fridge and coffee machine. **Device health** and a **unified notification inbox** are first-class features.

> **Status:** Phase 1 of 20 (Design) is complete. Implementation starts in Phase 2.

## Start here

| Document | Description |
|----------|-------------|
| [Home-Hub-Design.pdf](Home-Hub-Design.pdf) | **Main design document**: the whole design in one read (19 pages, diagrams included) |
| [Home-Hub-Design.html](Home-Hub-Design.html) | The same document as a web page (diagrams render live in the browser) |
| [design/](design/00-README.md) | Detailed design documents |

## What it does

- **One dashboard**: every device on one live, glanceable screen, organised by room.
- **Health first**: shows what is offline or faulty, why, and for how long, with an incident history and uptime figures.
- **One inbox**: notifications from every device, de-duplicated, with acknowledge/snooze, delivered by Web Push and email.
- **Direct control**: open the gate (with PIN confirmation), set the heating, clean rooms, print a PDF, all from the browser.
- **Driver SDK**: new integrations are written as Java plug-ins or as sidecar containers in any language, connected over MQTT.
- **Local and secure**: local protocols first, VPN-only remote access, encrypted credentials.

It is deliberately **not** a Home Assistant clone. It focuses on the devices this household owns and grows through open standards (MQTT, Tuya, Zigbee2MQTT, Matter).

## Planned devices

| Device | Integration | Phase |
|--------|-------------|-------|
| Remootio main gate | Local WebSocket API | 12 |
| HP & other printers | IPP Everywhere | 13 |
| Routers, VPN router, network clients | Router APIs + ARP / mDNS / SNMP | 14 |
| X-Sense fire alarms | Official local MQTT bridge (SBS50) | 15 |
| Viessmann ViCare heating | Official cloud API (rate-limited) | 16 |
| ProBreeze dehumidifier | Tuya local protocol | 17 |
| Roborock vacuum | Local protocol via python-roborock sidecar | 18 |
| IP cameras | ONVIF + go2rtc (WebRTC) | 19 |
| Fridge & coffee machine | Vendor API (Home Connect / SmartThings / ThinQ) | 20 |

## Technology

| Layer | Choice |
|-------|--------|
| Backend | Java 25, Spring Boot 4, Maven multi-module |
| Data | PostgreSQL 17 + TimescaleDB |
| Messaging | In-process event bus; Eclipse Mosquitto (MQTT 5) at the edge |
| API | REST (OpenAPI 3) + WebSocket / STOMP |
| Frontend | React 19, TypeScript, Vite, Tailwind, shadcn/ui (PWA) |
| Media | go2rtc (RTSP → WebRTC) |
| Deployment | Docker Compose on a single home server, Caddy for TLS |

## Repository layout

```
my-home-dashboard/
├── Home-Hub-Design.pdf / .html   main design document
├── design/                       detailed design
│   ├── 00-README.md              index and reading guide
│   ├── 01-architecture-overview.md
│   ├── 02-domain-model.md
│   ├── 03-driver-sdk-and-messaging.md
│   ├── 04-sequence-diagrams.md
│   ├── 05-integration-specs.md
│   ├── 06-security-and-deployment.md
│   ├── 07-ui-ux-design.md
│   ├── 08-decisions-and-open-questions.md
│   ├── 09-roadmap.md
│   └── diagrams/                 rendered PNGs of every Mermaid diagram
└── LICENSE
```

From Phase 2, source code will be added under `backend/`, `ui/`, `sidecars/` and `deploy/`.

## Roadmap

There are 20 small phases, each ending with a running system. See [design/09-roadmap.md](design/09-roadmap.md) for the full plan.

1. **Design** ✅
2. Repository and build · 3. Domain core and event bus · 4. Persistence · 5. REST + WebSocket API · 6. Dockerise · 7. UI shell
8. Driver SDK · 9. Security · 10. Health monitor · 11. Notifications
12. Remootio · 13. Printers · 14. Network · 15. MQTT + X-Sense · 16. ViCare · 17. Tuya / ProBreeze · 18. Roborock · 19. Cameras · 20. Appliances, hardening, v1.0

## License

See [LICENSE](LICENSE).
