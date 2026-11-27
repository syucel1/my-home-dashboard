package dev.homehub.web.system;

/**
 * Basic facts about the running hub.
 *
 * @param name application name
 * @param version build version, or {@code dev} when running without build info
 * @param javaVersion Java runtime version
 */
public record SystemInfo(String name, String version, String javaVersion) {}
