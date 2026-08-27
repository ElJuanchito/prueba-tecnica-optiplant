package com.optiplant.inventory.iam.domain.exception;

/**
 * Thrown when a caller who is not a corporate {@code ADMIN} attempts to mutate
 * a resource belonging to a branch other than their own (RN-08, RN-14). The web
 * layer maps this to {@code 403 Forbidden} — unlike the authentication
 * exceptions, there is no existence-leak concern here: the caller is already
 * authenticated, so a distinct status does not reveal anything new.
 */
public class CrossBranchMutationException extends RuntimeException {

	public CrossBranchMutationException(String message) {
		super(message);
	}
}
