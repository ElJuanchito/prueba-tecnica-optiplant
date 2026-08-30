package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort;
import com.optiplant.inventory.purchases.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.ActiveProductRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.BranchDescriptorRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.ConversionFactorRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.ProductDescriptorRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.SupplierDescriptorRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.UserDescriptorRow;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link PurchaseReferencePort} (design §4, §6.1, §6.3).
 */
@Component
public class PurchaseReferenceAdapter implements PurchaseReferencePort {

	private final PurchaseReferenceSpringDataRepository referenceRepository;

	public PurchaseReferenceAdapter(PurchaseReferenceSpringDataRepository referenceRepository) {
		this.referenceRepository = referenceRepository;
	}

	@Override
	public void requireActiveProducts(Collection<UUID> productExternalIds) {
		if (productExternalIds == null || productExternalIds.isEmpty()) {
			return;
		}
		Set<UUID> requested = new HashSet<>(productExternalIds);
		Set<UUID> foundActive = referenceRepository.findActiveProductExternalIds(requested).stream()
				.map(ActiveProductRow::getExternalId)
				.collect(Collectors.toSet());
		for (UUID id : requested) {
			if (!foundActive.contains(id)) {
				throw new ProductNotFoundException(id);
			}
		}
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
	public Map<UUID, SupplierDescriptor> findSuppliers(Collection<UUID> supplierExternalIds) {
		if (supplierExternalIds == null || supplierExternalIds.isEmpty()) {
			return Map.of();
		}
		return referenceRepository.findSupplierDescriptors(supplierExternalIds).stream()
				.collect(Collectors.toMap(
						SupplierDescriptorRow::getExternalId,
						row -> new SupplierDescriptor(row.getExternalId(), row.getTaxId(), row.getName())
				));
	}

	@Override
	public Map<UUID, BigDecimal> conversionFactors(Collection<ProductUnitRef> productUnits) {
		if (productUnits == null || productUnits.isEmpty()) {
			return Map.of();
		}
		Set<UUID> productIds = productUnits.stream().map(ProductUnitRef::productExternalId).collect(Collectors.toSet());
		Set<UUID> unitIds = productUnits.stream().map(ProductUnitRef::unitOfMeasureExternalId).collect(Collectors.toSet());
		List<ConversionFactorRow> rows = referenceRepository.findConversionFactors(productIds, unitIds);

		Map<String, BigDecimal> factorLookup = new HashMap<>();
		for (ConversionFactorRow row : rows) {
			factorLookup.put(row.getProductExternalId() + ":" + row.getUnitOfMeasureExternalId(), row.getConversionFactor());
		}

		Map<UUID, BigDecimal> result = new HashMap<>();
		for (ProductUnitRef ref : productUnits) {
			BigDecimal factor = factorLookup.get(ref.productExternalId() + ":" + ref.unitOfMeasureExternalId());
			if (factor != null) {
				result.put(ref.productExternalId(), factor);
			}
		}
		return result;
	}
}
