package com.optiplant.inventory.catalog.domain.exception;

import java.util.UUID;

/**
 * Thrown when a category lookup by {@code external_id} — a direct {@code get},
 * an {@code edit}/{@code disable}/{@code enable}, or a product create/edit
 * referencing a category — names no category (R-04, R-06, R-09). Mirrors
 * {@code iam}'s {@code BranchNotFoundException}; the web layer maps it to
 * {@code 404 category_not_found}.
 */
public class CategoryNotFoundException extends RuntimeException {

	public CategoryNotFoundException(UUID externalId) {
		super("No category found for external id " + externalId);
	}
}
