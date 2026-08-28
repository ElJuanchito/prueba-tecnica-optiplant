package com.optiplant.inventory.catalog.application.port.out;

import com.optiplant.inventory.catalog.domain.model.ActiveFilter;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductSort;
import com.optiplant.inventory.catalog.domain.model.ProductSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for product persistence (design §5.3). Named for the need, not
 * the technology: no method mentions JPA, SQL or a table. Only
 * {@code external_id}-shaped UUIDs and domain types cross it — the adapter
 * resolves the internal numeric {@code id} itself and never returns one.
 */
public interface ProductRepositoryPort {

	/** The full aggregate, units included. */
	Optional<Product> findByExternalId(UUID externalId);

	/**
	 * R-06 / R-09 uniqueness check against the normalized SKU.
	 * {@code excludingExternalId} is the row being edited — {@code null} on create —
	 * so editing a product to its own current SKU is not a spurious conflict.
	 */
	boolean existsBySku(String normalizedSku, UUID excludingExternalId);

	/** Persists the product and its inline units in one transaction (R-06). */
	Product create(NewProduct newProduct);

	Product update(UUID externalId, ProductUpdate update);

	Product setActive(UUID externalId, boolean active, Instant updatedAt);

	/** R-08 — used only by {@code changeBaseUnit}, which is wired in S7. */
	Product setBaseUnit(UUID externalId, String baseUnit, Instant updatedAt);

	ProductPage list(ProductFilter filter);

	record NewProduct(String sku, String name, String description, UUID categoryExternalId, String baseUnit,
			List<NewUnitRow> units) {
	}

	record NewUnitRow(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {
	}

	record ProductUpdate(String sku, String name, String description, UUID categoryExternalId, Instant updatedAt) {
	}

	record ProductFilter(String q, UUID categoryExternalId, ActiveFilter active, ProductSort sort, boolean ascending,
			int page, int size) {
	}

	record ProductPage(List<ProductSummary> content, long totalElements, int page, int size) {
	}
}
