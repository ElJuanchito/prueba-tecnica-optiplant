package com.optiplant.inventory.purchases.application.port.out;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Secondary port resolving catalog, branch, user and supplier references for {@code purchases}
 * (design §4). Every lookup is a batch — one call per request, never one per line
 * (RNF-PER-01/02). Native {@code external_id -> id} resolution in the adapter creates no module
 * edge (§2.1).
 */
public interface PurchaseReferencePort {

	/**
	 * @throws com.optiplant.inventory.purchases.domain.exception.ProductNotFoundException
	 *     any id names nothing, or a disabled product (R-08)
	 */
	void requireActiveProducts(Collection<UUID> productExternalIds);

	Map<UUID, ProductDescriptor> findProducts(Collection<UUID> productExternalIds);

	Map<UUID, BranchDescriptor> findBranches(Collection<UUID> branchExternalIds);

	Map<UUID, UserDescriptor> findUsers(Collection<UUID> userExternalIds);

	Map<UUID, SupplierDescriptor> findSuppliers(Collection<UUID> supplierExternalIds);

	/**
	 * Batch {@code conversion_factor} lookup (R-09): product {@code external_id} to the factor from
	 * the requested unit to the product's base unit. A missing entry means no conversion exists.
	 */
	Map<UUID, BigDecimal> conversionFactors(Collection<ProductUnitRef> productUnits);

	record ProductUnitRef(UUID productExternalId, UUID unitOfMeasureExternalId) {
	}

	record ProductDescriptor(UUID externalId, String sku, String name) {
	}

	record BranchDescriptor(UUID externalId, String name) {
	}

	record UserDescriptor(UUID externalId, String username) {
	}

	record SupplierDescriptor(UUID externalId, String taxId, String name) {
	}
}
