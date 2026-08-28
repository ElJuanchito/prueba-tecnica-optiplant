package com.optiplant.inventory.iam.application.port.in;

import java.util.UUID;

/**
 * Credential-based login (RF-SEG-01, CU-SEG-01): verifies username/password and, on
 * success, issues a short-lived access token plus a persisted, rotating refresh token.
 */
public interface AuthenticateUseCase {

	LoginResult login(LoginCommand command);

	/** {@code clientIp} feeds the login-throttle key alongside the username. */
	record LoginCommand(String username, String password, String clientIp) {
	}

	/** {@code branchId}, {@code branchName}, and {@code branchCode} are {@code null} for a corporate {@code ADMIN}. */
	record LoginResult(String accessToken, long expiresInSeconds, String refreshToken, String role, UUID branchId,
			String branchName, String branchCode) {
	}
}
