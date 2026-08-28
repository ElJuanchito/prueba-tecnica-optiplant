package com.optiplant.inventory.iam.domain.exception;

import java.util.UUID;

/**
 * Thrown when {@code ManageUsersUseCase#edit}/{@code #disable} is called with
 * an {@code external_id} that names no user. Not in the design's literal
 * exception enumeration — added because both operations need a genuine
 * {@code 404} for an unknown target, the same kind of justified,
 * beyond-the-literal-list addition earlier slices made (e.g.
 * {@code TooManyLoginAttemptsException} in slice 2a). No existence-leak
 * concern applies: the caller is an already-authenticated {@code ADMIN}
 * operating on an {@code external_id} it supplied itself.
 */
public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(UUID externalId) {
		super("No user found for external id " + externalId);
	}
}
