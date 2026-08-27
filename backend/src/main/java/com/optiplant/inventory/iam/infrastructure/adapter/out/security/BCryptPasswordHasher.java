package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import com.optiplant.inventory.iam.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Verifies against {@code users.password_hash}, which every seed row stores as BCrypt. */
@Component
public class BCryptPasswordHasher implements PasswordHasherPort {

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	@Override
	public boolean matches(String rawPassword, String hashedPassword) {
		return encoder.matches(rawPassword, hashedPassword);
	}
}
