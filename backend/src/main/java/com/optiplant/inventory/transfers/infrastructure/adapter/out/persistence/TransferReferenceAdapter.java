package com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.transfers.domain.model.BranchReference;
import com.optiplant.inventory.transfers.domain.model.ProductReference;
import com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence.TransferReferenceSpringDataRepository.BranchDescriptorRow;
import com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence.TransferReferenceSpringDataRepository.ProductDescriptorRow;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single {@link TransferReferencePort} implementation (design §5.2, §6.1). No {@code @Entity}
 * spans a boundary: {@code branches}/{@code products}/{@code users} are resolved through
 * {@link TransferReferenceSpringDataRepository}'s native queries, exactly as {@code inventory}'s
 * {@code ForeignKeyResolverSpringDataRepository} resolves the same tables.
 */
@Component
public class TransferReferenceAdapter implements TransferReferencePort {

	private final TransferReferenceSpringDataRepository referenceRepository;

	public TransferReferenceAdapter(TransferReferenceSpringDataRepository referenceRepository) {
		this.referenceRepository = referenceRepository;
	}

	@Override
	public void requireActiveBranch(UUID branchExternalId) {
		referenceRepository.findActiveBranchIdByExternalId(branchExternalId)
				.orElseThrow(() -> new BranchNotFoundException(branchExternalId));
	}

	@Override
	public Optional<ProductReference> findProduct(UUID productExternalId) {
		return referenceRepository.findActiveProductDescriptor(productExternalId)
				.map(row -> new ProductReference(row.getExternalId(), row.getSku(), row.getName()));
	}

	@Override
	public Map<UUID, ProductReference> findProducts(Collection<UUID> productExternalIds) {
		if (productExternalIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, ProductReference> result = new HashMap<>();
		for (ProductDescriptorRow row : referenceRepository.findProductDescriptors(productExternalIds)) {
			result.put(row.getExternalId(), new ProductReference(row.getExternalId(), row.getSku(), row.getName()));
		}
		return result;
	}

	@Override
	public Map<UUID, BranchReference> findBranches(Collection<UUID> branchExternalIds) {
		if (branchExternalIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, BranchReference> result = new HashMap<>();
		for (BranchDescriptorRow row : referenceRepository.findBranchDescriptors(branchExternalIds)) {
			result.put(row.getExternalId(), new BranchReference(row.getExternalId(), row.getName()));
		}
		return result;
	}
}
