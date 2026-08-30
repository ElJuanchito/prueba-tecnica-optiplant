package com.optiplant.inventory.inventory.infrastructure.adapter.out.stock;

import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.ForeignKeyResolverSpringDataRepository;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.ForeignKeyResolverSpringDataRepository.IdExternalIdRow;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.KardexMovementSpringDataRepository;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.KardexMovementSpringDataRepository.ProductUnitCostRow;
import com.optiplant.inventory.shared.stock.OutboundValuationPort;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link OutboundValuationPort} (contract D-2, design §2.2) over
 * {@code idx_kardex_reference}: R-20 requires a received item to be valued at the same unit cost
 * its matching {@code TRANSFER_OUT} used, and {@code transfer_items} has no column to cache it in
 * (§2.5). One batch query per receipt, no N+1 (RNF-PER-01).
 */
@Component
public class InventoryOutboundValuationAdapter implements OutboundValuationPort {

	private final KardexMovementSpringDataRepository kardexRepository;
	private final ForeignKeyResolverSpringDataRepository foreignKeyResolver;

	public InventoryOutboundValuationAdapter(KardexMovementSpringDataRepository kardexRepository,
			ForeignKeyResolverSpringDataRepository foreignKeyResolver) {
		this.kardexRepository = kardexRepository;
		this.foreignKeyResolver = foreignKeyResolver;
	}

	@Override
	public Map<UUID, BigDecimal> outboundUnitCosts(UUID branchExternalId, String referenceType, String referenceId) {
		Long branchId = foreignKeyResolver.findBranchIdByExternalId(branchExternalId).orElse(-1L);
		List<ProductUnitCostRow> rows = kardexRepository.findOutboundUnitCosts(branchId, referenceType, referenceId);
		if (rows.isEmpty()) {
			return Map.of();
		}
		List<Long> productIds = rows.stream().map(ProductUnitCostRow::getProductId).distinct().toList();
		Map<Long, UUID> productExternalIds = new HashMap<>();
		for (IdExternalIdRow row : foreignKeyResolver.findProductExternalIds(productIds)) {
			productExternalIds.put(row.getId(), row.getExternalId());
		}
		Map<UUID, BigDecimal> result = new HashMap<>();
		for (ProductUnitCostRow row : rows) {
			UUID productExternalId = productExternalIds.get(row.getProductId());
			if (productExternalId != null) {
				result.put(productExternalId, row.getUnitCost());
			}
		}
		return result;
	}
}
