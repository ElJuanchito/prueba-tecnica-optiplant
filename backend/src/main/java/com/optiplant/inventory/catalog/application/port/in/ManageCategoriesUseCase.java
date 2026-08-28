package com.optiplant.inventory.catalog.application.port.in;

import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort.CategoryPage;
import com.optiplant.inventory.catalog.domain.model.ActiveFilter;
import com.optiplant.inventory.catalog.domain.model.CategorySummary;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Manage the category catalog (CU-INV-01) — {@code ADMIN}-only for mutations,
 * open to every authenticated role for reads (enforced by {@code SecurityConfig}'s
 * {@code /api/catalog/**} matchers, added in S3). Every mutation writes an audit
 * entry in the same transaction (R-15, CLAUDE.md's synchronous-effects invariant).
 *
 * <p>Mutations take an {@link AuthenticatedPrincipal actor}; <strong>reads do
 * not</strong> (R-16, D-7). The read path has no way to see who is asking, so it
 * structurally cannot vary by caller — the catalog has no branch dimension.
 */
public interface ManageCategoriesUseCase {

	/** Paginated, active-only by default (R-12). */
	CategoryPage list(CategoryQuery query);

	/**
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException
	 *     when {@code externalId} names no category
	 */
	CategorySummary get(UUID externalId);

	/**
	 * @throws com.optiplant.inventory.catalog.domain.exception.DuplicateCategoryNameException
	 *     on a case-insensitive name collision (R-02)
	 * @throws IllegalArgumentException
	 *     when the name is blank or longer than 100 characters
	 */
	CategorySummary create(AuthenticatedPrincipal actor, CreateCategoryCommand command);

	/**
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException
	 *     when {@code externalId} names no category
	 * @throws com.optiplant.inventory.catalog.domain.exception.DuplicateCategoryNameException
	 *     on a case-insensitive name collision with another category (R-02, R-03)
	 */
	CategorySummary edit(AuthenticatedPrincipal actor, UUID externalId, EditCategoryCommand command);

	/**
	 * Sets {@code is_active = false} and advances {@code updated_at}. Never a
	 * physical delete. Idempotent: disabling an already-disabled category succeeds
	 * and changes nothing (R-03).
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException
	 *     when {@code externalId} names no category
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryInUseException
	 *     when the category still has at least one active product (R-04)
	 */
	CategorySummary disable(AuthenticatedPrincipal actor, UUID externalId);

	/**
	 * Sets {@code is_active = true} and advances {@code updated_at}. Idempotent:
	 * enabling an already-active category succeeds and changes nothing (R-03, R-11).
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException
	 *     when {@code externalId} names no category
	 */
	CategorySummary enable(AuthenticatedPrincipal actor, UUID externalId);

	record CreateCategoryCommand(String name, String description) {
	}

	record EditCategoryCommand(String name, String description) {
	}

	record CategoryQuery(String name, ActiveFilter active, int page, int size) {
	}
}
