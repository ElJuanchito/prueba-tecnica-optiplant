package com.optiplant.inventory.inventory.application.port.in;

import com.optiplant.inventory.inventory.domain.model.MovementReceipt;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Register a manual stock movement against the caller's own branch (CU-INV-05, CU-INV-06, design
 * §5.1). Authorization (a branch-scoped {@code ADMIN} or {@code BRANCH_MANAGER} for
 * {@link #adjust}, plus {@code OPERATOR} for {@link #writeOff}, R-13) is enforced by
 * {@code SecurityConfig}'s {@code /api/inventory/} matchers, added in a later slice.
 */
public interface RegisterStockMovementUseCase {

	/**
	 * @throws com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException
	 *     when {@code actor} is a corporate {@code ADMIN} (contract §5, PA-02)
	 * @throws com.optiplant.inventory.inventory.domain.exception.AdjustmentReasonRequiredException
	 *     on a blank or absent reason (RN-11, R-07)
	 * @throws com.optiplant.inventory.inventory.domain.exception.AdjustmentWithoutDifferenceException
	 *     when the counted quantity equals the current balance (R-08)
	 * @throws IllegalArgumentException when the counted quantity is negative (RN-01)
	 */
	MovementReceipt adjust(AuthenticatedPrincipal actor, AdjustStockCommand command);

	/**
	 * @throws com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException
	 *     when {@code actor} is a corporate {@code ADMIN} (contract §5, PA-02)
	 * @throws com.optiplant.inventory.inventory.domain.exception.AdjustmentReasonRequiredException
	 *     on a blank or absent reason (RN-11, R-07)
	 * @throws com.optiplant.inventory.inventory.domain.exception.InsufficientStockException
	 *     when the quantity exceeds the available balance (R-11, RN-01)
	 */
	MovementReceipt writeOff(AuthenticatedPrincipal actor, WriteOffCommand command);

	record AdjustStockCommand(UUID productExternalId, BigDecimal countedQuantity, String reason) {
	}

	record WriteOffCommand(UUID productExternalId, BigDecimal quantity, String reason) {
	}
}
