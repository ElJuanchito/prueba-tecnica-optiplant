package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown when a {@code unit_name} is repeated within the same product (R-13). The
 * message carries the offending value; the web layer maps it to {@code 409
 * duplicate_product_unit}. The schema half of the same invariant is
 * {@code uq_product_unit} ({@code 01-init-schema.sql:113}).
 */
public class DuplicateProductUnitException extends RuntimeException {

	public DuplicateProductUnitException(String message) {
		super(message);
	}
}
