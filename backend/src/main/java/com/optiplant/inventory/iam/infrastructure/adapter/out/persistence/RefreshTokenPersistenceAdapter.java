package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.domain.model.RefreshTokenGrant;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import com.optiplant.inventory.iam.infrastructure.adapter.out.security.Sha256TokenDigest;
import com.optiplant.inventory.iam.infrastructure.config.JwtProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

	private final RefreshTokenSpringDataRepository refreshTokenRepository;
	private final UserSpringDataRepository userRepository;
	private final JwtProperties jwtProperties;
	private final Sha256TokenDigest tokenDigest;

	public RefreshTokenPersistenceAdapter(RefreshTokenSpringDataRepository refreshTokenRepository,
			UserSpringDataRepository userRepository, JwtProperties jwtProperties, Sha256TokenDigest tokenDigest) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.jwtProperties = jwtProperties;
		this.tokenDigest = tokenDigest;
	}

	@Override
	public void persist(NewRefreshToken newRefreshToken) {
		Long userId = userRepository.findIdByExternalId(newRefreshToken.userExternalId())
				.orElseThrow(() -> new IllegalStateException(
						"No user found for external id " + newRefreshToken.userExternalId()));

		Instant now = Instant.now();
		RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
		entity.setUserId(userId);
		entity.setFamilyId(newRefreshToken.familyId());
		entity.setTokenHash(tokenDigest.hex(newRefreshToken.rawToken()));
		entity.setIssuedAt(now);
		entity.setLastUsedAt(now);
		entity.setExpiresAt(now.plus(jwtProperties.refreshAbsolute()));
		refreshTokenRepository.save(entity);
	}

	@Override
	public Optional<RefreshTokenGrant> findByRawToken(String rawToken) {
		return refreshTokenRepository.findByTokenHash(tokenDigest.hex(rawToken)).map(this::toGrant);
	}

	@Override
	public void revoke(UUID externalId, RevocationReason reason) {
		refreshTokenRepository.revokeByExternalId(externalId, Instant.now(), reason.name());
	}

	@Override
	public void revokeFamily(UUID familyId, RevocationReason reason) {
		refreshTokenRepository.revokeByFamilyId(familyId, Instant.now(), reason.name());
	}

	private RefreshTokenGrant toGrant(RefreshTokenJpaEntity entity) {
		UUID userExternalId = userRepository.findExternalIdById(entity.getUserId())
				.orElseThrow(() -> new IllegalStateException("No user found for id " + entity.getUserId()));
		RevocationReason revokedReason = entity.getRevokedReason() != null
				? RevocationReason.valueOf(entity.getRevokedReason())
				: null;
		return new RefreshTokenGrant(entity.getExternalId(), userExternalId, entity.getFamilyId(), entity.getIssuedAt(),
				entity.getLastUsedAt(), entity.getExpiresAt(), entity.getRevokedAt(), revokedReason);
	}
}
