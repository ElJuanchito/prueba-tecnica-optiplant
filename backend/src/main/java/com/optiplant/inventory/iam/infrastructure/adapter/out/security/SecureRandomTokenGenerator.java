package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import com.optiplant.inventory.iam.application.port.out.SecretTokenGeneratorPort;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 256 bits of {@link SecureRandom}, URL-safe encoded — plenty of margin against
 * offline brute force, per design decision "refresh tokens are stored as a
 * deterministic SHA-256 hex digest, not BCrypt". */
@Component
public class SecureRandomTokenGenerator implements SecretTokenGeneratorPort {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
