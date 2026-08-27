package com.optiplant.inventory.iam.infrastructure.config;

import com.optiplant.inventory.iam.application.port.out.RefreshTokenPolicyConfigPort;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Wraps {@link JwtProperties#refreshInactivity()} behind the application-facing
 * {@link RefreshTokenPolicyConfigPort} so {@code SessionRefreshService} never
 * imports an {@code iam.infrastructure} type directly. */
@Component
class RefreshTokenPolicyConfigAdapter implements RefreshTokenPolicyConfigPort {

	private final JwtProperties jwtProperties;

	RefreshTokenPolicyConfigAdapter(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
	}

	@Override
	public Duration idleWindow() {
		return jwtProperties.refreshInactivity();
	}
}
