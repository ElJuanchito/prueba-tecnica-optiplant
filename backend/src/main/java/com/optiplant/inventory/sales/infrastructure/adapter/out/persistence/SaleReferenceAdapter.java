package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleReferenceSpringDataRepository.BranchDescriptorRow;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleReferenceSpringDataRepository.ProductDescriptorRow;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleReferenceSpringDataRepository.UserDescriptorRow;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link SaleReferencePort} (design §5, §6.1, §6.2, §6.5).
 */
@Component
public class SaleReferenceAdapter implements SaleReferencePort {

	private final SaleReferenceSpringDataRepository referenceRepository;

	public SaleReferenceAdapter(SaleReferenceSpringDataRepository referenceRepository) {
		this.referenceRepository = referenceRepository;
	}

	@Override
	public void requireActiveProduct(UUID productExternalId) {
		referenceRepository.findActiveProductIdByExternalId(productExternalId)
				.orElseThrow(() -> new ProductNotFoundException(productExternalId));
	}

	@Override
	public Map<UUID, ProductDescriptor> findProducts(Collection<UUID> productExternalIds) {
		if (productExternalIds == null || productExternalIds.isEmpty()) {
			return Map.of();
		}
		return referenceRepository.findProductDescriptors(productExternalIds).stream()
				.collect(Collectors.toMap(
						ProductDescriptorRow::getExternalId,
						row -> new ProductDescriptor(row.getExternalId(), row.getSku(), row.getName())
				));
	}

	@Override
	public Map<UUID, BranchDescriptor> findBranches(Collection<UUID> branchExternalIds) {
		if (branchExternalIds == null || branchExternalIds.isEmpty()) {
			return Map.of();
		}
		return referenceRepository.findBranchDescriptors(branchExternalIds).stream()
				.collect(Collectors.toMap(
						BranchDescriptorRow::getExternalId,
						row -> new BranchDescriptor(row.getExternalId(), row.getName())
				));
	}

	@Override
	public Map<UUID, UserDescriptor> findUsers(Collection<UUID> userExternalIds) {
		if (userExternalIds == null || userExternalIds.isEmpty()) {
			return Map.of();
		}
		return referenceRepository.findUserDescriptors(userExternalIds).stream()
				.collect(Collectors.toMap(
						UserDescriptorRow::getExternalId,
						row -> new UserDescriptor(row.getExternalId(), row.getUsername())
				));
	}

	@Override
	public Optional<BigDecimal> findConversionFactor(UUID productExternalId, UUID unitOfMeasureExternalId) {
		return referenceRepository.findConversionFactor(productExternalId, unitOfMeasureExternalId);
	}

	@Override
	public Optional<ServiceUserSubject> findExternalCredentialSubject(UUID userExternalId) {
		return referenceRepository.findExternalCredentialSubject(userExternalId)
				.map(row -> new ServiceUserSubject(row.getExternalId(), row.getUsername(), Role.valueOf(row.getRole())));
	}
}
