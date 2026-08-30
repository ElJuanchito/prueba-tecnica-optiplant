package com.optiplant.inventory.pricing.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port resolving catalog and IAM references for pricing (design §5).
 */
public interface PricingReferencePort {

	/**
	 * @throws com.optiplant.inventory.pricing.domain.exception.ProductNotFoundException if not found or disabled
	 */
	void requireActiveProduct(UUID productExternalId);

	/**
	 * @throws com.optiplant.inventory.pricing.domain.exception.BranchNotFoundException if not found or inactive
	 */
	void requireActiveBranch(UUID branchExternalId);

	/**
	 * Returns the external ID of the default price list configured for the given branch.
	 */
	Optional<UUID> findDefaultPriceListForBranch(UUID branchExternalId);
}
