package com.optiplant.inventory.catalog.application.port.in;

import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Manage the units of measure of a product (CU-INV-02) — {@code ADMIN}-only for
 * mutations, open to every authenticated role for reads (enforced by
 * {@code SecurityConfig}'s {@code /api/catalog/**} matchers). Every mutation
 * writes an audit entry in the same transaction, {@code entityName =
 * "product_units"}, {@code branchId = null} (R-15, R-16, CLAUDE.md's
 * synchronous-effects invariant).
 *
 * <p>Mutations take an {@link AuthenticatedPrincipal actor}; <strong>reads do
 * not</strong> (R-16, D-7). The unit collection is <strong>not paginated</strong>:
 * it is bounded by the product itself and by {@code uq_product_unit} — the
 * justified exception to RNF-PER-04 (contract §6.3).
 */
public interface ManageProductUnitsUseCase {

	/**
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code productExternalId} names no product
	 */
	List<ProductUnit> list(UUID productExternalId);

	/**
	 * Adds an alternative unit. When {@code command.defaultSaleUnit()} is {@code
	 * true}, the previous default is cleared within the same transaction (R-14).
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code productExternalId} names no product
	 * @throws com.optiplant.inventory.catalog.domain.exception.DuplicateProductUnitException
	 *     when {@code unitName} is already defined on the product (R-13)
	 * @throws com.optiplant.inventory.catalog.domain.exception.InvalidConversionFactorException
	 *     when the factor is {@code <= 0}, or the unit is a base-unit homonym with a factor other than 1 (R-13)
	 * @throws IllegalArgumentException when {@code unitName} is malformed
	 */
	ProductUnit add(AuthenticatedPrincipal actor, UUID productExternalId, UnitCommand command);

	/**
	 * Replaces an existing unit. When {@code command.defaultSaleUnit()} is {@code
	 * true}, every sibling default is cleared first (R-14); when it is {@code
	 * false} and the unit was the default, the product legitimately ends with no
	 * default (R-14's fourth scenario).
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code productExternalId} names no product
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductUnitNotFoundException
	 *     when {@code unitExternalId} names no unit of that product — including one that belongs to another product
	 * @throws com.optiplant.inventory.catalog.domain.exception.DuplicateProductUnitException
	 *     when the new {@code unitName} collides with another unit of the product (R-13)
	 * @throws com.optiplant.inventory.catalog.domain.exception.InvalidConversionFactorException
	 *     when the factor is {@code <= 0}, or the unit is a base-unit homonym with a factor other than 1 (R-13)
	 */
	ProductUnit replace(AuthenticatedPrincipal actor, UUID productExternalId, UUID unitExternalId, UnitCommand command);

	/**
	 * Deletes a unit. The one physical deletion in the module: no other table
	 * references {@code product_units} and it holds no history (R-13). Deleting
	 * the current default leaves the product with none.
	 *
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException
	 *     when {@code productExternalId} names no product
	 * @throws com.optiplant.inventory.catalog.domain.exception.ProductUnitNotFoundException
	 *     when {@code unitExternalId} names no unit of that product
	 */
	void delete(AuthenticatedPrincipal actor, UUID productExternalId, UUID unitExternalId);

	record UnitCommand(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {
	}
}
