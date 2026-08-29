package com.optiplant.inventory.inventory.application.port.out;

import com.optiplant.inventory.inventory.domain.model.DateRange;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.KardexPage;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Secondary port for {@code kardex_movements} persistence (design §5.2). Insert-only — no update,
 * no delete method exists here: R-17's append-only guarantee holds at the port boundary too.
 */
public interface KardexRepositoryPort {

	KardexMovement append(NewMovement movement);

	/** No lock, ordered {@code created_at} ascending (T-05, R-16). */
	KardexPage list(KardexFilter filter);

	boolean hasAnyMovement(UUID productExternalId);

	/** The insertable fields of a Kardex row — mirrors {@link KardexMovement.Draft} at the port boundary. */
	record NewMovement(UUID branchExternalId, UUID productExternalId, StockMovementType movementType,
			Quantity quantity, UnitCost unitCost, BigDecimal totalCost, BigDecimal previousStock,
			BigDecimal resultingStock, String referenceType, String referenceId, String notes,
			UUID userExternalId) {
	}

	/**
	 * @param branchExternalId {@code null} means unrestricted — a corporate {@code ADMIN} reading
	 *                         any branch (contract §5)
	 */
	record KardexFilter(UUID branchExternalId, UUID productExternalId, StockMovementType movementType,
			DateRange range, int page, int size) {
	}
}
