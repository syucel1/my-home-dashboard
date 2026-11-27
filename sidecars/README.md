# sidecars/

Out-of-process drivers in other languages (mostly Python). Each runs in its own container and talks to the hub over the MQTT Driver Protocol.

| Sidecar | Library | Phase |
|---------|---------|-------|
| `roborock` | python-roborock | 18 |
| `xsense` (cloud fallback) | python-xsense | 15 |

The shared helper package `homehub-sidecar` comes in **phase 15**. See [design/03-driver-sdk-and-messaging.md](../design/03-driver-sdk-and-messaging.md).
