package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown when disabling a category that still has at least one <strong>active</strong>
 * product (R-04). A category with no products or only inactive ones can be
 * disabled. The web layer maps it to {@code 409 category_in_use}.
 */
public class CategoryInUseException extends RuntimeException {

	public CategoryInUseException(String message) {
		super(message);
	}
}
