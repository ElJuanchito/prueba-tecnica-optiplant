package com.optiplant.inventory.iam.infrastructure.config;

import java.time.Duration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Signing key and TTLs for the access/refresh token pair, per Product decision
 * P1: the 15-minute access TTL and 8-hour refresh idle window are
 * configuration, never code constants.
 *
 * <p>Validation runs at context startup: in the {@code prod} profile there is
 * no default value, so the absence of {@code JWT_SECRET} breaks startup
 * instead of surfacing on the first login attempt.
 */
@Validated
@ConfigurationProperties(prefix = "optiplant.jwt")
public record JwtProperties(

		/**
		 * HMAC-SHA256 requires a key of at least 256 bits. Without the minimum, a
		 * short key would be accepted at startup and fail at signing time.
		 */
		@NotBlank(message = "optiplant.jwt.secret es obligatorio")
		@Size(min = 32, message = "optiplant.jwt.secret requiere al menos 32 caracteres (256 bits) para HMAC-SHA256")
		String secret,

		/** Access token time-to-live. */
		@DefaultValue("15m")
		@NotNull
		Duration accessTtl,

		/** Refresh token idle window: rejected once {@code last_used_at} is older than this. */
		@DefaultValue("8h")
		@NotNull
		Duration refreshInactivity,

		/** Absolute refresh token expiry, regardless of activity. */
		@DefaultValue("7d")
		@NotNull
		Duration refreshAbsolute) {
}
