package com.optiplant.inventory.catalog.application.port.out;

import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for per-product unit-of-measure persistence (design §5.3). Named
 * for the need, not the technology: no method mentions JPA, SQL or a table. Only
 * {@code external_id}-shaped UUIDs and domain types cross it.
 *
 * <p>{@link #find} is <strong>scoped by product</strong>: a unit that exists but
 * hangs off another product resolves to {@link Optional#empty()}, so the service
 * can answer {@code 404} rather than {@code 200}.
 *
 * <p>{@link #clearDefaultSaleUnit} clears {@code is_default_sale_unit} on every
 * unit of the product and flushes. It MUST be called before any write that sets
 * the flag — the ordering is load-bearing (design §8.2): {@code
 * uq_product_units_single_default} is a partial unique index PostgreSQL checks per
 * statement and cannot defer, so a {@code SET TRUE} issued before the {@code SET
 * FALSE} lands would transiently leave two {@code TRUE} rows and abort the
 * transaction.
 */
public interface ProductUnitRepositoryPort {

	List<ProductUnit> findByProduct(UUID productExternalId);

	Optional<ProductUnit> find(UUID productExternalId, UUID unitExternalId);

	void clearDefaultSaleUnit(UUID productExternalId);

	ProductUnit add(UUID productExternalId, NewUnitRow unit);

	ProductUnit replace(UUID productExternalId, UUID unitExternalId, NewUnitRow unit);

	void delete(UUID productExternalId, UUID unitExternalId);

	record NewUnitRow(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {
	}
}
