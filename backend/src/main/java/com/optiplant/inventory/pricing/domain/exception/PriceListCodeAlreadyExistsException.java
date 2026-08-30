package com.optiplant.inventory.pricing.domain.exception;

/**
 * Thrown when attempting to create a price list with a code that already exists (R-15).
 * Maps to {@code 409 price_list_code_already_exists}.
 */
public class PriceListCodeAlreadyExistsException extends RuntimeException {

	public PriceListCodeAlreadyExistsException(String code) {
		super("Price list code already exists: " + code);
	}
}
