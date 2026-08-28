package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown when creating or editing a product would collide with another product's
 * SKU (R-06, R-09). The SKU is compared after the normalization {@code Sku}
 * applies (trim + upper-case), so {@code abc-1} and {@code ABC-1} collide. The
 * message carries the offending value; the web layer maps it to {@code 409
 * duplicate_sku}. The schema half of the same invariant is the existing
 * {@code UNIQUE (sku)} index ({@code 01-init-schema.sql:92}).
 */
public class DuplicateSkuException extends RuntimeException {

	public DuplicateSkuException(String message) {
		super(message);
	}
}
