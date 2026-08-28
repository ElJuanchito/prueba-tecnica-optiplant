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

	/** Revokes every still-live session of the given user, across every family —
	 * unlike {@link #revokeFamily}, this closes every device at once
	 * (user-administration "User disable is logical and revokes active
	 * sessions": disabling MUST immediately revoke every one of the user's
	 * refresh tokens, P2/P4). */
	void revokeAllForUser(UUID userExternalId, RevocationReason reason);

	record NewRefreshToken(UUID userExternalId, UUID familyId, String rawToken) {
	}
}
