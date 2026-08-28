package com.optiplant.inventory.iam.application.port.out;

public interface PasswordHasherPort {

	boolean matches(String rawPassword, String hashedPassword);

	/** Hashes a new password for storage (user-administration "Successful user
	 * creation": persisted with a BCrypt-hashed password). */
	String hash(String rawPassword);
}
