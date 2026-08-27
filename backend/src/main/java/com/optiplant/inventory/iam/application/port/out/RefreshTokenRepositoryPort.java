package com.optiplant.inventory.iam.application.port.out;

import java.util.UUID;

/**
 * Persists refresh-token sessions. Slice 2a only needs {@link #persist}; lookup,
 * rotation and revocation are added in slice 2b's {@code SessionRefreshService} flow.
 */
public interface RefreshTokenRepositoryPort {

	/**
	 * Persists a new session. The implementation hashes {@code rawToken} before
	 * writing it — the raw value never reaches storage (design decision: refresh
	 * tokens are stored as a deterministic SHA-256 hex digest).
	 */
	void persist(NewRefreshToken newRefreshToken);

	record NewRefreshToken(UUID userExternalId, UUID familyId, String rawToken) {
	}
}
