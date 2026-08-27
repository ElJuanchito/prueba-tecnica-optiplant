package com.optiplant.inventory.iam.domain.service;

import com.optiplant.inventory.iam.domain.model.RefreshTokenGrant;
import com.optiplant.inventory.iam.domain.model.RefreshTokenState;
import java.time.Duration;
import java.time.Instant;

/**
 * Pure evaluation of a refresh-token grant's validity, per design's REFRESH data
 * flow: revoked (presented again) is treated as reuse, an absolute {@code expires_at}
 * in the past rejects, idle past {@code refreshInactivity} rejects, otherwise the
 * grant is valid. No I/O, no side effects — testable without Spring or a fixed
 * {@link java.time.Clock} injection: the caller supplies {@code now} directly.
 */
public class RefreshTokenPolicy {

	public RefreshTokenState evaluate(RefreshTokenGrant grant, Instant now, Duration idleWindow) {
		if (grant.isRevoked()) {
			return RefreshTokenState.REUSE_DETECTED;
		}
		if (!grant.expiresAt().isAfter(now)) {
			return RefreshTokenState.EXPIRED;
		}
		if (grant.lastUsedAt().plus(idleWindow).isBefore(now)) {
			return RefreshTokenState.IDLE_EXPIRED;
		}
		return RefreshTokenState.VALID;
	}
}
