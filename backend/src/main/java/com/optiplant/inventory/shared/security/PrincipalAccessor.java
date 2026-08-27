package com.optiplant.inventory.shared.security;

import java.util.Optional;

/**
 * Reads the {@link AuthenticatedPrincipal} of the current request without
 * exposing any Spring Security type to consumers outside {@code iam}.
 *
 * <p>Every business module depends on this interface, never on
 * {@code SecurityContextHolder} directly — the single implementation lives in
 * {@code iam.infrastructure.adapter.out.security}, keeping all Spring-Security
 * coupling inside that module (design decision: "principal accessor is an
 * interface in {@code shared}, implemented in {@code iam}").
 */
public interface PrincipalAccessor {

	/** Empty when the current request has no authenticated principal. */
	Optional<AuthenticatedPrincipal> current();

	/**
	 * @return the current principal
	 * @throws IllegalStateException when the request is unauthenticated
	 */
	AuthenticatedPrincipal require();
}
