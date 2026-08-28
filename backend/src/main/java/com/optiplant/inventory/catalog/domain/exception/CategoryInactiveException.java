package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown when a product would be created in, moved into, or re-enabled under an
 * inactive category (R-05, R-11). Not raised by the category use case itself —
 * it belongs to the product path — but declared with the category exceptions
 * because the category is the resource whose state triggers it. The web layer
 * maps it to {@code 409 category_inactive}.
 */
public class CategoryInactiveException extends RuntimeException {

	public CategoryInactiveException(String message) {
		super(message);
	}
}
