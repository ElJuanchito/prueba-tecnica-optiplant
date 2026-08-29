package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown when a conversion factor is {@code null} or not strictly positive, or
 * when a unit homonymous with the product's base unit carries a factor other than
 * {@code 1} (R-13). The base unit is worth exactly one base unit; accepting
 * anything else would let two contradictory conversions of the same name coexist.
 * The web layer maps it to {@code 400 invalid_conversion_factor}. The schema half
 * of the positivity check is {@code CHECK (conversion_factor > 0)}
 * ({@code 01-init-schema.sql:110}).
 */
public class InvalidConversionFactorException extends RuntimeException {

	public InvalidConversionFactorException(String message) {
		super(message);
	}
}
