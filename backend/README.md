# backend/

The Home Hub backend: a **Java 25 / Spring Boot 4.1 modular monolith** built with Maven. See [design/01-architecture-overview.md](../design/01-architecture-overview.md) for the full picture.

## Modules

| Module | Package | Purpose | Depends on |
|--------|---------|---------|------------|
| `hub-domain` | `dev.homehub.domain` | Device / Capability / State / Command / Event model. Plain Java only | n/a |
| `hub-driver-sdk` | `dev.homehub.sdk` | Driver SPI and `DriverContext`; the only API drivers may use | domain |
| `hub-core` | `dev.homehub.core` | Registry, state store, commands, event bus, health, notifications | domain, sdk |
| `hub-persistence` | `dev.homehub.persistence` | PostgreSQL + TimescaleDB, Flyway (phase 4) | core |
| `hub-web` | `dev.homehub.web` | REST `/api/v1`, WebSocket, security | core |
| `drivers/` | `dev.homehub.driver.*` | In-process device drivers (phase 8+) | sdk only |
| `hub-app` | `dev.homehub.app` | Spring Boot main class, config, runnable jar, architecture tests | everything |

The dependency rules are enforced on every build by `hub-app/src/test/java/dev/homehub/architecture/ArchitectureTest.java`:
- the domain stays framework-free;
- the SDK depends on the domain only;
- core never sees persistence, web or drivers;
- drivers use the SDK only;
- web never talks to persistence directly.

## Prerequisites

- **JDK 25**, e.g. Eclipse Temurin 25. Check with `java -version`.
- **Maven 3.9+**. Check with `mvn -v`.

The build stops early with a clear message if either is too old.

## Build

```bash
mvn verify                    # compile + Error Prone + tests + ArchUnit + format check
mvn spotless:apply            # auto-format all Java sources (palantir-java-format)
mvn spotless:apply verify     # usual local loop: format, then full check
mvn -Dspotless.check.skip=true verify    # skip the format check while experimenting
```

What `verify` runs:
- **Error Prone 2.48** runs as a compiler plugin and catches common bugs at compile time.
- **Spotless** checks the formatting. CI fails if the code isn't formatted, so run `spotless:apply` before committing.
- The **tests**: the app starts on a random port, `/actuator/health` reports `UP`, `/api/v1/system/info` answers, and the ArchUnit module rules hold.
- The **enforcer** checks that the JDK is 25+ and Maven is 3.9+.

## Run

```bash
mvn -DskipTests package
java -jar hub-app/target/hub-app.jar
```

| URL | What |
|-----|------|
| <http://localhost:8080/actuator/health> | Health (`{"status":"UP"}`), plus `/liveness` and `/readiness` probes |
| <http://localhost:8080/actuator/info> | Build and Java info |
| <http://localhost:8080/api/v1/system/info> | Name, version and Java runtime of the hub |

Virtual threads are on (`spring.threads.virtual.enabled=true`). The config lives in `hub-app/src/main/resources/application.yml`.

## IDE setup (IntelliJ IDEA)

1. Open the `backend/pom.xml` as a project and set the Project SDK to JDK 25.
2. Install the **palantir-java-format** plugin and enable it, so IDE formatting matches Spotless.
3. Run `HomeHubApplication` from `hub-app`.

## CI

`.github/workflows/ci.yml` runs on every push to `main` and on pull requests:
- `mvn -B verify` on Temurin 25;
- a Trivy dependency vulnerability scan, report-only for now.

Dependabot proposes weekly updates for Maven dependencies and GitHub Actions.
