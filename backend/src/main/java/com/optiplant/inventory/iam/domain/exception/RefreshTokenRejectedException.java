package com.optiplant.inventory.iam.domain.exception;

/**
 * Thrown when a presented refresh token cannot be honoured: not found, revoked
 * (reuse), expired, or idle past the configured window. Deliberately a single
 * exception type — the HTTP layer maps every case to the same generic {@code 401},
 * so a caller cannot distinguish "reuse detected" from "simply expired".
 */
public class RefreshTokenRejectedException extends RuntimeException {

	public RefreshTokenRejectedException(String message) {
		super(message);
	}
}
