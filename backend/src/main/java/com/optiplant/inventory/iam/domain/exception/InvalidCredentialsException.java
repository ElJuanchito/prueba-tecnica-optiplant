package com.optiplant.inventory.iam.domain.exception;

/**
 * Thrown when login credentials do not match an active account.
 *
 * <p>Deliberately generic: per CU-SEG-01 EX-01 the caller must never learn whether the
 * submitted username exists. {@link UserDisabledException} is mapped to the exact same
 * HTTP response for the same reason.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid username or password");
	}
}
