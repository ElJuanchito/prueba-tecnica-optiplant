package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.infrastructure.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

	private final RefreshTokenSpringDataRepository refreshTokenRepository;
	private final UserSpringDataRepository userRepository;
	private final JwtProperties jwtProperties;

	public RefreshTokenPersistenceAdapter(RefreshTokenSpringDataRepository refreshTokenRepository,
			UserSpringDataRepository userRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.jwtProperties = jwtProperties;
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
		entity.setTokenHash(sha256Hex(newRefreshToken.rawToken()));
		entity.setIssuedAt(now);
		entity.setLastUsedAt(now);
		entity.setExpiresAt(now.plus(jwtProperties.refreshAbsolute()));
		refreshTokenRepository.save(entity);
	}

	/**
	 * Deterministic digest so a future rotation/reuse lookup can be a unique-index
	 * read on {@code token_hash} (design decision: refresh tokens are stored as a
	 * SHA-256 hex digest, never BCrypt — BCrypt's salt would make that lookup
	 * impossible). Inlined here for slice 2a's write-only need; slice 2b's
	 * {@code SessionRefreshService} needs this same digest to look a token back up,
	 * at which point it is expected to move into its own {@code Sha256TokenDigest}
	 * adapter shared by both call sites, per design's package layout.
	 */
	private static String sha256Hex(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
