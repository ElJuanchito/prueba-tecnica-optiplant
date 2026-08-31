package com.optiplant.inventory.analytics.infrastructure.config;

import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security authentication token holding an {@link AuthenticatedPrincipal} for the external availability path (design §7).
 */
class ExternalAvailabilityAuthenticationToken extends AbstractAuthenticationToken {

	private final AuthenticatedPrincipal principal;

	ExternalAvailabilityAuthenticationToken(AuthenticatedPrincipal principal) {
		super(List.of(new SimpleGrantedAuthority(principal.role().name())));
		this.principal = principal;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}
}
