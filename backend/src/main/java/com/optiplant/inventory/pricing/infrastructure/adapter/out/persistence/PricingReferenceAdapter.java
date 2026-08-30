package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.pricing.application.port.out.PricingReferencePort;
import com.optiplant.inventory.pricing.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.ProductNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link PricingReferencePort} (design §5, §6.1).
 */
@Component
public class PricingReferenceAdapter implements PricingReferencePort {

	private final PricingReferenceSpringDataRepository referenceRepository;

	public PricingReferenceAdapter(PricingReferenceSpringDataRepository referenceRepository) {
		this.referenceRepository = referenceRepository;
	}

	@Override
	public void requireActiveProduct(UUID productExternalId) {
		referenceRepository.findActiveProductIdByExternalId(productExternalId)
				.orElseThrow(() -> new ProductNotFoundException(productExternalId));
	}

	@Override
	public void requireActiveBranch(UUID branchExternalId) {
		referenceRepository.findActiveBranchIdByExternalId(branchExternalId)
				.orElseThrow(() -> new BranchNotFoundException(branchExternalId));
	}

	@Override
	public Optional<UUID> findDefaultPriceListForBranch(UUID branchExternalId) {
		return referenceRepository.findDefaultPriceListExternalIdForBranch(branchExternalId);
	}
}
