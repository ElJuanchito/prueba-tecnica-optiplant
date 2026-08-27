package com.optiplant.inventory.iam.application.service;

import com.optiplant.inventory.iam.application.port.in.RefreshSessionUseCase;
import com.optiplant.inventory.iam.application.port.out.AccessTokenIssuerPort;
import com.optiplant.inventory.iam.application.port.out.RefreshTokenPolicyConfigPort;
import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.SecretTokenGeneratorPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort;
import com.optiplant.inventory.iam.domain.exception.RefreshTokenRejectedException;
import com.optiplant.inventory.iam.domain.model.RefreshTokenGrant;
import com.optiplant.inventory.iam.domain.model.RefreshTokenState;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.iam.domain.service.RefreshTokenPolicy;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates refresh per design's Data Flow "REFRESH", one transaction: lookup by
 * hash → policy evaluation → on reuse, revoke the whole family; otherwise revoke the
 * presented token (ROTATED) and insert a successor sharing the same {@code family_id}.
 */
@Service
public class SessionRefreshService implements RefreshSessionUseCase {

	private final RefreshTokenRepositoryPort refreshTokenRepository;
	private final SecretTokenGeneratorPort refreshTokenGenerator;
	private final AccessTokenIssuerPort accessTokenIssuer;
	private final UserRepositoryPort userRepository;
	private final RefreshTokenPolicyConfigPort policyConfig;
	private final RefreshTokenPolicy policy = new RefreshTokenPolicy();

	public SessionRefreshService(RefreshTokenRepositoryPort refreshTokenRepository,
			SecretTokenGeneratorPort refreshTokenGenerator, AccessTokenIssuerPort accessTokenIssuer,
			UserRepositoryPort userRepository, RefreshTokenPolicyConfigPort policyConfig) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.accessTokenIssuer = accessTokenIssuer;
		this.userRepository = userRepository;
		this.policyConfig = policyConfig;
	}

	@Override
	@Transactional
	public RefreshResult refresh(String presentedRefreshToken) {
		RefreshTokenGrant grant = refreshTokenRepository.findByRawToken(presentedRefreshToken)
				.orElseThrow(() -> new RefreshTokenRejectedException("Refresh token not found"));

		RefreshTokenState state = policy.evaluate(grant, Instant.now(), policyConfig.idleWindow());
		if (state == RefreshTokenState.REUSE_DETECTED) {
			// An already-revoked token was presented again: revoke the whole family
			// (design decision "reuse detection revokes the token family, not the
			// whole user" — P4 keeps the caller's other devices alive).
			refreshTokenRepository.revokeFamily(grant.familyId(), RevocationReason.REUSE_DETECTED);
			throw new RefreshTokenRejectedException("Refresh token reuse detected");
		}
		if (state == RefreshTokenState.EXPIRED || state == RefreshTokenState.IDLE_EXPIRED) {
			throw new RefreshTokenRejectedException("Refresh token expired");
		}

		UserAccount user = userRepository.findByExternalId(grant.userExternalId())
				.orElseThrow(() -> new RefreshTokenRejectedException("User no longer exists"));

		// A user (or their branch) disabled after login must not keep rotating: without
		// this, the session would survive until the 7-day absolute window closed, which
		// defeats the revocable-session premise the refresh_tokens table exists for.
		// Revoking the family — not just the presented token — closes every device this
		// session chain reached.
		if (!user.active()) {
			refreshTokenRepository.revokeFamily(grant.familyId(), RevocationReason.USER_DISABLED);
			throw new RefreshTokenRejectedException("User is disabled");
		}

		refreshTokenRepository.revoke(grant.externalId(), RevocationReason.ROTATED);

		AuthenticatedPrincipal principal = new AuthenticatedPrincipal(user.externalId(), user.username(), user.role(),
				user.branchExternalId());
		AccessTokenIssuerPort.IssuedAccessToken accessToken = accessTokenIssuer.issue(principal);

		String rawSuccessor = refreshTokenGenerator.generate();
		refreshTokenRepository
				.persist(new RefreshTokenRepositoryPort.NewRefreshToken(user.externalId(), grant.familyId(), rawSuccessor));

		return new RefreshResult(accessToken.token(), accessToken.expiresInSeconds(), rawSuccessor);
	}
}
