package com.optiplant.inventory.iam.domain.exception;

import java.util.UUID;

/**
 * Thrown when {@code ManageBranchesUseCase#edit}/{@code #disable} is called with
 * an {@code external_id} that names no branch. Added beyond the design's literal
 * exception enumeration because both operations need a genuine {@code 404} for an
 * unknown target, mirroring {@code UserNotFoundException} from slice 5a.
 */
public class BranchNotFoundException extends RuntimeException {

	public BranchNotFoundException(UUID externalId) {
		super("No branch found for external id " + externalId);
	}
}
