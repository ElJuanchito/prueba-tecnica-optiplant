package com.optiplant.inventory.inventory.infrastructure.adapter.out.stock;

import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.BranchInventoryJpaEntity;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.BranchInventorySpringDataRepository;
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
	private final BranchInventorySpringDataRepository branchInventoryRepository;

	public InventoryOutboundValuationAdapter(KardexMovementSpringDataRepository kardexRepository,
			ForeignKeyResolverSpringDataRepository foreignKeyResolver,
			BranchInventorySpringDataRepository branchInventoryRepository) {
		this.kardexRepository = kardexRepository;
		this.foreignKeyResolver = foreignKeyResolver;
		this.branchInventoryRepository = branchInventoryRepository;
	}

	@Override
	public Map<UUID, BigDecimal> outboundUnitCosts(UUID branchExternalId, String referenceType, String referenceId) {
		Long branchId = foreignKeyResolver.findBranchIdByExternalId(branchExternalId).orElse(-1L);
		List<ProductUnitCostRow> rows = kardexRepository.findOutboundUnitCosts(branchId, referenceType, referenceId);
		Map<UUID, BigDecimal> result = new HashMap<>();
		if (!rows.isEmpty()) {
			List<Long> productIds = rows.stream().map(ProductUnitCostRow::getProductId).distinct().toList();
			Map<Long, UUID> productExternalIds = new HashMap<>();
			for (IdExternalIdRow row : foreignKeyResolver.findProductExternalIds(productIds)) {
				productExternalIds.put(row.getId(), row.getExternalId());
			}
			for (ProductUnitCostRow row : rows) {
				UUID productExternalId = productExternalIds.get(row.getProductId());
				if (productExternalId != null && row.getUnitCost() != null) {
					result.put(productExternalId, row.getUnitCost());
				}
			}
		}
		if (result.isEmpty() && branchId > 0) {
			// Fallback to origin branch's current average cost in branch_inventories for seeded transfers
			List<BranchInventoryJpaEntity> inventories = branchInventoryRepository.findByBranchId(branchId);
			if (!inventories.isEmpty()) {
				List<Long> productIds = inventories.stream().map(BranchInventoryJpaEntity::getProductId).distinct().toList();
				Map<Long, UUID> productExternalIds = new HashMap<>();
				for (IdExternalIdRow row : foreignKeyResolver.findProductExternalIds(productIds)) {
					productExternalIds.put(row.getId(), row.getExternalId());
				}
				for (BranchInventoryJpaEntity inv : inventories) {
					UUID pExtId = productExternalIds.get(inv.getProductId());
					if (pExtId != null) {
						result.put(pExtId, inv.getAverageCost() != null ? inv.getAverageCost() : BigDecimal.ZERO.setScale(4));
					}
				}
			}
		}
		return result;
	}
}
