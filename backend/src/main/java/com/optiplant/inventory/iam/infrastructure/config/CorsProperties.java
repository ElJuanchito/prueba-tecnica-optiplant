package com.optiplant.inventory.iam.infrastructure.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Explicit CORS origin allow-list (RNF-SEC-06) — no wildcard, credentials off. No
 * {@code @DefaultValue}: each profile supplies its own list explicitly
 * ({@code application-dev.yml} for local/Compose, {@code application-prod.yml} from an
 * environment variable), the same pattern {@code JwtProperties.secret} already uses so
 * a missing prod value fails at startup rather than at the first cross-origin request.
 */
@Validated
@ConfigurationProperties(prefix = "optiplant.cors")
public record CorsProperties(

		@NotEmpty(message = "optiplant.cors.allowed-origins es obligatorio")
		List<String> allowedOrigins) {
}
