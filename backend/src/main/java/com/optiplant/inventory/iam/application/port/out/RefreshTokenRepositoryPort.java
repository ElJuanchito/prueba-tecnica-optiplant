package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.iam.domain.model.RefreshTokenGrant;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists and looks up refresh-token sessions. The raw token never reaches an
 * implementation's storage — {@link #persist} hashes it before writing, and
 * {@link #findByRawToken} hashes the presented value before querying (design
 * decision: refresh tokens are stored as a deterministic SHA-256 hex digest).
 */
public interface RefreshTokenRepositoryPort {

	/**
	 * Persists a new session. The implementation hashes {@code rawToken} before
	 * writing it — the raw value never reaches storage (design decision: refresh
	 * tokens are stored as a deterministic SHA-256 hex digest).
	 */
	void persist(NewRefreshToken newRefreshToken);

	/** Empty when no session's hash matches {@code rawToken} (not-found → 401). */
	Optional<RefreshTokenGrant> findByRawToken(String rawToken);

	/** Revokes exactly one still-live session (LOGOUT or ROTATED). A no-op if the
	 * session is already revoked. */
	void revoke(UUID externalId, RevocationReason reason);

	/** Revokes every still-live session sharing {@code familyId} (reuse detection;
	 * P4 keeps other families/devices alive). */
	void revokeFamily(UUID familyId, RevocationReason reason);

	record NewRefreshToken(UUID userExternalId, UUID familyId, String rawToken) {
	}
}
