package com.optiplant.inventory.iam.application.port.in;

/**
 * Revokes the presented refresh token only (P4 — other devices/sessions survive).
 * Idempotent: presenting an unknown or already-revoked token is a no-op, not an
 * error, so a caller can never learn whether it hit "already logged out" versus
 * "never existed".
 */
public interface LogoutUseCase {

	void logout(String presentedRefreshToken);
}
