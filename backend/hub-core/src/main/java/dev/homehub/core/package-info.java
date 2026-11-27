/**
 * Core services: device registry, state store, command service, event bus, health monitor, notification engine,
 * rules engine, credential vault and rate-budget manager.
 *
 * <p>Must not depend on persistence, web, app or driver code. See {@code design/01-architecture-overview.md}.
 * Populated from phase 3.
 */
package dev.homehub.core;
