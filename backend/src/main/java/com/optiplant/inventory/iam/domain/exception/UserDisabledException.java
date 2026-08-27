package com.optiplant.inventory.iam.domain.exception;

/**
 * Thrown when a matched user, or the branch that user is assigned to, is disabled.
 *
 * <p>The controller maps this to the exact same generic response as
 * {@link InvalidCredentialsException}: a distinct response would let a caller
 * distinguish "wrong password" from "account exists but disabled", which is the same
 * user-existence leak CU-SEG-01 EX-01 forbids for unknown usernames.
 */
public class UserDisabledException extends RuntimeException {

	public UserDisabledException() {
		super("User account is disabled");
	}
}
