package com.optiplant.inventory.catalog.application.port.out;

import com.optiplant.inventory.catalog.domain.model.ActiveFilter;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.CategorySummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for category persistence (design §5.3). Named for the need, not
 * the technology: no method mentions JPA, SQL or a table. Only
 * {@code external_id}-shaped UUIDs and domain types cross it — the adapter
 * resolves the internal numeric {@code id} itself and never returns one.
 */
public interface CategoryRepositoryPort {

	Optional<CategorySummary> findByExternalId(UUID externalId);

	/** Cheap reference for the product path, without the summary's product count. */
	Optional<CategoryRef> findRefByExternalId(UUID externalId);

	/**
	 * R-02's case-insensitive uniqueness check. {@code excludingExternalId} is the
	 * row being edited — {@code null} on create — so renaming a category to its own
	 * current name is not a spurious conflict.
	 */
	boolean existsByNameIgnoringCase(String comparisonKey, UUID excludingExternalId);

	/** R-04: whether the category has at least one active product. */
	boolean hasActiveProducts(UUID externalId);

	CategorySummary create(NewCategory newCategory);

	CategorySummary update(UUID externalId, CategoryUpdate update);

	CategorySummary setActive(UUID externalId, boolean active, Instant updatedAt);

	CategoryPage list(CategoryFilter filter);

	record NewCategory(String name, String description) {
	}

	record CategoryUpdate(String name, String description, Instant updatedAt) {
	}

	record CategoryFilter(String name, ActiveFilter active, int page, int size) {
	}

	record CategoryPage(List<CategorySummary> content, long totalElements, int page, int size) {
	}
}
