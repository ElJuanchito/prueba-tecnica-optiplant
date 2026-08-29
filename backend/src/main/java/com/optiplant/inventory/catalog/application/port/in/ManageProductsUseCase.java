package com.optiplant.inventory.catalog.application.port.in;

import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort.ProductPage;
import com.optiplant.inventory.catalog.domain.model.ActiveFilter;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductSort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Manage the product catalog (CU-INV-01) — {@code ADMIN}-only for mutations, open
 * to every authenticated role for reads (enforced by {@code SecurityConfig}'s
 * {@code /api/catalog/**} matchers). Every mutation writes an audit entry in the
 * same transaction (R-15, CLAUDE.md's synchronous-effects invariant).
 *
 * <p>Mutations take an {@link AuthenticatedPrincipal actor}; <strong>reads do
 * not</strong> (R-16, D-7). The read path has no way to see who is asking, so it
 * structurally cannot vary by caller — the catalog has no branch dimension.
 */
public interface ManageProductsUseCase {

	/** Paginated, active-only by default (R-12). */
	ProductPage list(ProductQuery query);

	/**
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code externalId} names no product. An inactive product is still
	 *     returned with {@code active = false} (R-10).
	 */
	Product get(UUID externalId);

	/**
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException
	 *     when {@code categoryExternalId} names no category (R-05)
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryInactiveException
	 *     when the referenced category is inactive (R-05)
	 * @throws com.optiplant.inventory.catalog.domain.exception.DuplicateSkuException
	 *     on a SKU collision (R-06)
	 * @throws IllegalArgumentException
	 *     when SKU or base unit is malformed
	 */
	Product create(AuthenticatedPrincipal actor, CreateProductCommand command);

	/**
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code externalId} names no product
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException
	 *     when {@code categoryExternalId} names no category (R-05, R-09)
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryInactiveException
	 *     when the product would be moved into an inactive category (R-05)
	 * @throws com.optiplant.inventory.catalog.domain.exception.DuplicateSkuException
	 *     when the SKU belongs to another product (R-09)
	 */
	Product edit(AuthenticatedPrincipal actor, UUID externalId, EditProductCommand command);

	/**
	 * Sets {@code is_active = false} and advances {@code updated_at}. Never a
	 * physical delete; balances, Kardex and sales rows are untouched (R-10).
	 * Idempotent.
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code externalId} names no product
	 */
	Product disable(AuthenticatedPrincipal actor, UUID externalId);

	/**
	 * Sets {@code is_active = true} and advances {@code updated_at}. Idempotent.
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code externalId} names no product
	 * @throws com.optiplant.inventory.catalog.domain.exception.CategoryInactiveException
	 *     when the product's category is inactive — re-enabling must not recreate the
	 *     inconsistency R-04/R-05 prevent (R-11)
	 */
	Product enable(AuthenticatedPrincipal actor, UUID externalId);

	/**
	 * R-08. Exposed by {@code PATCH /api/catalog/products/{externalId}/base-unit}
	 * (DT-07, paid once {@code inventory} implemented
	 * {@code shared/stock/ProductStockPresencePort}). The precondition check and the
	 * write share this method's single {@code @Transactional} boundary, so a
	 * concurrent goods receipt cannot create the first Kardex movement between the
	 * check and the commit.
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code externalId} names no product
	 * @throws com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException
	 *     with {@link com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException.Reason#HAS_HISTORY}
	 *     when the product has balances or Kardex history in the old base unit
	 *     (RN-13), or with
	 *     {@link com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException.Reason#PRECONDITION_UNVERIFIABLE}
	 *     when the stock-presence port cannot answer — the policy fails closed, never
	 *     open (contract §2.2)
	 */
	Product changeBaseUnit(AuthenticatedPrincipal actor, UUID externalId, String newBaseUnit);

	record CreateProductCommand(String sku, String name, String description, UUID categoryExternalId, String baseUnit,
			List<NewUnit> units) {
	}

	record NewUnit(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {
	}

	/** {@code baseUnit} is deliberately absent — it is fixed at creation in this change (PA-08, §6.2). */
	record EditProductCommand(String sku, String name, String description, UUID categoryExternalId) {
	}

	record ProductQuery(String q, UUID categoryExternalId, ActiveFilter active, ProductSort sort, boolean ascending,
			int page, int size) {
	}
}
