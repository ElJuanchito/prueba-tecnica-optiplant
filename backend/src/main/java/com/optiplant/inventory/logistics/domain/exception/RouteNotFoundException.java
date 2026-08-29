package com.optiplant.inventory.logistics.domain.exception;

import java.util.UUID;

/** Thrown when a route {@code external_id} names no row. The web layer maps this to {@code 404 route_not_found}. */
public class RouteNotFoundException extends RuntimeException {

	public RouteNotFoundException(UUID externalId) {
		super("No route found for external id " + externalId);
	}
}
