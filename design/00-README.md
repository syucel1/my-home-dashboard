# Home Hub: Design Pack

> **Status:** Phase 1 (Design) · Draft v0.1 · 2026-11-08
> **Owner:** Serkan Yucel

Home Hub is a self-hosted platform for the home. It brings every device in the house onto one clean browser dashboard. It has a Java backend and a modern web UI, and it runs entirely in Docker.

It is **not** a Home Assistant clone. The aim is to do a smaller set of things very well:

1. **One dashboard** for every device the household owns.
2. **Device health first.** You can always see what is offline or faulty, and why.
3. **One notification inbox** for alerts from every device.
4. **Direct control** of devices: open the gate, start the vacuum, set the heating, print a document.
5. **An extensible driver SDK**, so new integrations are cheap to add.

> **Start here:** the main design document `Home-Hub-Design.html` (also available as a PDF) sits at the top level of the project and summarises this whole pack in one read. The files in this `design/` folder hold the full detail.

## Documents

| # | Document | What it covers |
|---|----------|----------------|
| 01 | [Architecture Overview](01-architecture-overview.md) | Principles, tech stack, system context, component, container and deployment diagrams |
| 02 | [Domain Model](02-domain-model.md) | Device / Capability / State / Command / Event model, health model, notification model, database schema |
| 03 | [Driver SDK & Messaging](03-driver-sdk-and-messaging.md) | How integrations plug in: the in-process Java SPI and out-of-process sidecars over MQTT, plus the internal event bus |
| 04 | [Sequence Diagrams](04-sequence-diagrams.md) | 12 key runtime flows: start-up, discovery, gate command, events → notifications, health, cloud polling, camera, print, sidecar, network scan, fire alarm, login |
| 05 | [Integration Specs](05-integration-specs.md) | One spec per device: Remootio, ViCare, X-Sense, ProBreeze/Tuya, Roborock, printers, cameras, routers/network, fridge, coffee machine |
| 06 | [Security & Deployment](06-security-and-deployment.md) | Threat model, auth, credential vault, remote access, Docker Compose topology, backups |
| 07 | [UI/UX Design](07-ui-ux-design.md) | Information architecture, screens, dashboard layout, design system |
| 08 | [Decisions & Open Questions](08-decisions-and-open-questions.md) | Architecture Decision Records (ADRs) and things waiting on input |
| 09 | [Roadmap: 20 Phases](09-roadmap.md) | The delivery plan, from design to v1.0 |

## How to read this pack

- New to the project? Read **01**, then **09**.
- Building the backend? Read **02 → 03 → 04**.
- Adding a device? Read **03**, then the matching section of **05**.

All diagrams are written in **Mermaid**. They render natively on GitHub and GitLab, in IntelliJ (with the Mermaid plugin), in VS Code (Markdown Preview Mermaid Support) and on mermaid.live.

## Assumptions made in this draft

These are marked ⚠️ wherever they appear. They are listed in full in [08](08-decisions-and-open-questions.md#open-questions).

- The brands of the router, VPN router, cameras, fridge and coffee machine are **not yet known**. Those drivers are designed as a generic driver with vendor-specific variants.
- **Remote access is VPN-only.** Nothing is exposed directly to the internet.
- The hub runs on **one Docker host** on the home LAN, using Docker Compose.
- It is a **personal project** for now, but the driver SDK is designed so it could be open-sourced later.
