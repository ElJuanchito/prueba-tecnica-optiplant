package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Deterministic SHA-256 hex digest shared by both {@code RefreshTokenPersistenceAdapter}
 * call sites — writing a new session and looking one up by its presented raw token
 * (design decision: refresh tokens are stored as a deterministic SHA-256 hex digest,
 * never BCrypt, so {@code WHERE token_hash = ?} stays a unique-index read).
 */
@Component
public class Sha256TokenDigest {

	public String hex(String rawToken) {
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
