package com.optiplant.inventory.sales.infrastructure.config;

import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security authentication token holding an {@link AuthenticatedPrincipal} for the POS path (design §6.5).
 */
class ExternalSalesAuthenticationToken extends AbstractAuthenticationToken {

	private final AuthenticatedPrincipal principal;

	ExternalSalesAuthenticationToken(AuthenticatedPrincipal principal) {
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
