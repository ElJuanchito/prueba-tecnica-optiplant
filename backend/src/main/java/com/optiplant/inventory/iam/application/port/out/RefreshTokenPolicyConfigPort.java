package com.optiplant.inventory.iam.application.port.out;

import java.time.Duration;

/**
 * Exposes the refresh-token idle window (P1 — configuration, never a domain
 * constant) to {@code SessionRefreshService} without the application layer
 * importing {@code iam.infrastructure.config.JwtProperties} directly — ArchUnit's
 * {@code laCapaDeAplicacionNoConoceSusAdaptadores} forbids that import.
 */
public interface RefreshTokenPolicyConfigPort {

	Duration idleWindow();
}
