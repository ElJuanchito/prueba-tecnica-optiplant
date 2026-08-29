package com.optiplant.inventory.inventory.infrastructure.adapter.out.stock;

import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort;
import com.optiplant.inventory.shared.stock.ProductStockPresencePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link ProductStockPresencePort} (contract §2.1, design §6.1), turning
 * {@code catalog}'s fail-closed {@code StockPresence.UNKNOWN} placeholder into a real answer.
 *
 * <p>A product is untouched when it has no {@code branch_inventories} row with a non-zero
 * balance <strong>and</strong> no {@code kardex_movements} row at all, in any branch, ever —
 * the port's exact two-clause predicate.
 */
@Component
public class InventoryStockPresenceAdapter implements ProductStockPresencePort {

	private final BranchInventoryRepositoryPort branchInventoryRepository;
	private final KardexRepositoryPort kardexRepository;

	public InventoryStockPresenceAdapter(BranchInventoryRepositoryPort branchInventoryRepository,
			KardexRepositoryPort kardexRepository) {
		this.branchInventoryRepository = branchInventoryRepository;
		this.kardexRepository = kardexRepository;
	}

	@Override
	public boolean isProductUntouched(UUID productExternalId) {
		return !branchInventoryRepository.hasAnyBalance(productExternalId)
				&& !kardexRepository.hasAnyMovement(productExternalId);
	}
}
