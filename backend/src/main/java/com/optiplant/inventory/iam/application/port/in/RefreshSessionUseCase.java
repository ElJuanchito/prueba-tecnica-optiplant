package com.optiplant.inventory.iam.application.port.in;

/**
 * Rotates a refresh-token session (RF-SEG-01, CU-SEG-01): validates the presented
 * token, revokes it, and issues a new access/refresh pair sharing the same
 * {@code family_id} — per design's REFRESH data flow.
 */
public interface RefreshSessionUseCase {

	RefreshResult refresh(String presentedRefreshToken);

	record RefreshResult(String accessToken, long expiresInSeconds, String refreshToken) {
	}
}
