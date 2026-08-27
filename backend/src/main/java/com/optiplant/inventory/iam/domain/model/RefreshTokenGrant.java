package com.optiplant.inventory.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh-token session as loaded by a hash lookup ({@code RefreshTokenRepositoryPort
 * #findByRawToken}). {@code revokedAt}/{@code revokedReason} are {@code null} for a
 * still-live grant.
 */
public record RefreshTokenGrant(
		UUID externalId,
		UUID userExternalId,
		UUID familyId,
		Instant issuedAt,
		Instant lastUsedAt,
		Instant expiresAt,
		Instant revokedAt,
		RevocationReason revokedReason) {

	public boolean isRevoked() {
		return revokedAt != null;
	}
}
