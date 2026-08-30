package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when no price list was specified and the branch has no active default price list (R-10).
 * Maps to {@code 409 price_list_not_resolvable}.
 */
public class PriceListNotResolvableException extends RuntimeException {

	public PriceListNotResolvableException(String message) {
		super(message);
	}
}
