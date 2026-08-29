package com.optiplant.inventory.inventory.application.port.out;

import com.optiplant.inventory.inventory.domain.model.BranchAvailability;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.StockPage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for {@code branch_inventories} persistence (design §5.2). Named for the need,
 * not the technology: only {@code external_id}-shaped {@link UUID}s and domain types cross it.
 */
public interface BranchInventoryRepositoryPort {

	/** {@code SELECT ... FOR UPDATE} (T-02) — required before reading the balance a mutation derives from. */
	Optional<BranchInventory> lockForUpdate(UUID branchExternalId, UUID productExternalId);

	/**
	 * Creates the row on demand with zeroed balances and {@code min_stock_threshold} explicitly
	 * set to {@code 0} (F-3) — never inheriting the schema default of {@code 10.0000}, which would
	 * make a brand-new product fire {@code STOCK_MINIMUM} on its first movement.
	 */
	BranchInventory createZeroed(UUID branchExternalId, UUID productExternalId);

	BranchInventory save(BranchInventory inventory);

	/** No lock (T-05, RN-09). */
	StockPage list(StockFilter filter);

	/** CU-INV-04 — every active branch, read-only, no lock (RN-08, RN-09). */
	List<BranchAvailability> findAcrossActiveBranches(UUID productExternalId);

	/** One clause of {@code ProductStockPresencePort}'s two-clause predicate. */
	boolean hasAnyBalance(UUID productExternalId);

	record StockFilter(UUID branchExternalId, UUID productExternalId, boolean belowThresholdOnly, String sort,
			int page, int size) {
	}
}
