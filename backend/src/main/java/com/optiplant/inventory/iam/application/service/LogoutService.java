package com.optiplant.inventory.iam.application.service;

import com.optiplant.inventory.iam.application.port.in.LogoutUseCase;
import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes the presented refresh token only (design's LOGOUT data flow, P4). Silently
 * no-ops on an unknown or already-revoked token — idempotent, and consistent with the
 * project's no-existence-leak posture for authentication endpoints.
 */
@Service
public class LogoutService implements LogoutUseCase {

	private final RefreshTokenRepositoryPort refreshTokenRepository;

	public LogoutService(RefreshTokenRepositoryPort refreshTokenRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Override
	@Transactional
	public void logout(String presentedRefreshToken) {
		refreshTokenRepository.findByRawToken(presentedRefreshToken)
				.ifPresent(grant -> refreshTokenRepository.revoke(grant.externalId(), RevocationReason.LOGOUT));
	}
}
