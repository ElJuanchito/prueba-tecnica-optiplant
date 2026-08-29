package com.optiplant.inventory.logistics.domain.exception;

/**
 * Thrown when a route already exists for the ordered branch pair (R-23), mirroring
 * {@code uq_route_pair}. The web layer maps this to {@code 409 route_already_exists}.
 */
public class RouteAlreadyExistsException extends RuntimeException {

	public RouteAlreadyExistsException() {
		super("a route already exists for this ordered branch pair");
	}
}
