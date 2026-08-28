package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Sole implementation of {@link PrincipalAccessor} — every business module depends
 * on the {@code shared} interface, never on {@link SecurityContextHolder} directly
 * (design decision: "principal accessor is an interface in shared, implemented in
 * iam"). {@link com.optiplant.inventory.iam.infrastructure.config.IamPrincipalConverter}
 * is what puts an {@link AuthenticatedPrincipal} in the {@link Authentication}'s
 * principal slot in the first place.
 */
@Component
public class SecurityContextPrincipalAccessor implements PrincipalAccessor {

	@Override
	public Optional<AuthenticatedPrincipal> current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
			return Optional.empty();
		}
		return Optional.of(principal);
	}

	@Override
	public AuthenticatedPrincipal require() {
		return current().orElseThrow(() -> new IllegalStateException("No authenticated principal"));
	}
}
