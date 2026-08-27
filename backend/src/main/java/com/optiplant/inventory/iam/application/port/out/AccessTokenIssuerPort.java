package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;

/** Issues signed access tokens. Slice 2a only issues; decoding is wired in slice 2b. */
public interface AccessTokenIssuerPort {

	IssuedAccessToken issue(AuthenticatedPrincipal principal);

	record IssuedAccessToken(String token, long expiresInSeconds) {
	}
}
