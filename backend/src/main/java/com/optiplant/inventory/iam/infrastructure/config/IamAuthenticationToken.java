package com.optiplant.inventory.iam.infrastructure.config;

import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * The {@link org.springframework.security.core.Authentication} produced by
 * {@link IamPrincipalConverter}. Spring Security's own {@code JwtAuthenticationToken}
 * keeps the raw {@code Jwt} as its principal; this subclass exists so {@code
 * getPrincipal()} returns the module-neutral {@link AuthenticatedPrincipal} instead —
 * exactly what design's data flow requires ({@code Authentication{principal = shared
 * AuthenticatedPrincipal}}), and what {@code SecurityContextPrincipalAccessor} reads.
 */
final class IamAuthenticationToken extends AbstractAuthenticationToken {

	private final AuthenticatedPrincipal principal;

	IamAuthenticationToken(AuthenticatedPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		// Stateless bearer auth: the credential (the JWT itself) was already consumed
		// by the decoder/filter before this token exists, so there is nothing to carry.
		return "";
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}
}
