package com.optiplant.inventory.iam.domain.exception;

/**
 * Thrown when attempting to create a branch with a {@code code} that is already in
 * use (branch-administration "Branch creation enforces a unique code",
 * RF-SEG-03).
 */
public class DuplicateBranchCodeException extends RuntimeException {

	public DuplicateBranchCodeException(String message) {
		super(message);
	}
}
