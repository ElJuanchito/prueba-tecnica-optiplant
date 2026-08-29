package com.optiplant.inventory.logistics.domain.exception;

/**
 * Thrown when a route is created with the same origin and destination branch (R-23), mirroring
 * {@code check_different_branches}. The web layer maps this to {@code 400 invalid_request}.
 */
public class SameBranchRouteException extends RuntimeException {

	public SameBranchRouteException() {
		super("origin and destination branch must differ");
	}
}
