package com.optiplant.inventory.sales.application.port.out;

import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port resolving catalog, branch and user references for sales (design §5).
 */
public interface SaleReferencePort {

	/**
	 * @throws com.optiplant.inventory.sales.domain.exception.ProductNotFoundException if the product is not found or disabled
	 */
	void requireActiveProduct(UUID productExternalId);

	/**
	 * Looks up product descriptors (in any state, active or disabled) for receipt/detail enrichment.
	 */
	Map<UUID, ProductDescriptor> findProducts(Collection<UUID> productExternalIds);

	/**
	 * Looks up branch descriptors for receipt/detail enrichment.
	 */
	Map<UUID, BranchDescriptor> findBranches(Collection<UUID> branchExternalIds);

	/**
	 * Looks up user descriptors for receipt/detail enrichment.
	 */
	Map<UUID, UserDescriptor> findUsers(Collection<UUID> userExternalIds);

	/**
	 * Looks up customer descriptors for receipt/detail enrichment (design §6).
	 */
	Map<UUID, CustomerDescriptor> findCustomers(Collection<UUID> customerExternalIds);

	/**
	 * Returns the conversion factor from the given unit to the product's base unit (R-07, D-2).
	 */
	Optional<BigDecimal> findConversionFactor(UUID productExternalId, UUID unitOfMeasureExternalId);

	/**
	 * Returns the service user's credentials and role for POS intake authentication (design §6.5, D-4).
	 */
	Optional<ServiceUserSubject> findExternalCredentialSubject(UUID userExternalId);

	record ProductDescriptor(UUID externalId, String sku, String name) {
	}

	record BranchDescriptor(UUID externalId, String name) {
	}

	record UserDescriptor(UUID externalId, String username) {
	}

	record CustomerDescriptor(UUID externalId, String name, String taxId) {
	}

	record ServiceUserSubject(UUID userExternalId, String username, Role role) {
	}
}
