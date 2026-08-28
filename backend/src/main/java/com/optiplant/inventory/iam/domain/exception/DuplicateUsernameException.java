package com.optiplant.inventory.iam.domain.exception;

/**
 * Thrown when a user create or edit would collide with an existing
 * {@code users.username} or {@code users.email} UNIQUE constraint
 * (user-administration "Duplicate username" / "Duplicate email"). Reused for
 * both conflicts — mirroring {@link CrossBranchMutationException} and {@link
 * RefreshTokenRejectedException}'s existing "one exception, message carries
 * the specifics" shape — rather than a second, near-identical
 * {@code DuplicateEmailException}: the design's own package layout names only
 * this one exception for slice 5a.
 */
public class DuplicateUsernameException extends RuntimeException {

	public DuplicateUsernameException(String message) {
		super(message);
	}
}
