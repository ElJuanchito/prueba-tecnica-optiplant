package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown when creating or editing a category would collide, case-insensitively
 * and after trimming, with another category's name (R-02). The message carries
 * the offending value; the web layer maps it to {@code 409
 * duplicate_category_name}. The schema half of the same invariant is
 * {@code uq_categories_name_ci} (S-4).
 */
public class DuplicateCategoryNameException extends RuntimeException {

	public DuplicateCategoryNameException(String message) {
		super(message);
	}
}
