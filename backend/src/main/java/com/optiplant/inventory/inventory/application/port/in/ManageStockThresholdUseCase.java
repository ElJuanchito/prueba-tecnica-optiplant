package com.optiplant.inventory.inventory.application.port.in;

import com.optiplant.inventory.inventory.domain.model.ThresholdView;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Set a product's minimum-stock threshold in the caller's own branch (CU-INV-07, design §5.1).
 * Writes no Kardex row — no balance changes (R-14).
 */
public interface ManageStockThresholdUseCase {

	/**
	 * @throws com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException
	 *     when {@code actor} is a corporate {@code ADMIN} (contract §5, PA-02)
	 * @throws IllegalArgumentException when {@code minStockThreshold} is {@code null} or negative (R-14)
	 */
	ThresholdView setThreshold(AuthenticatedPrincipal actor, UUID productExternalId, BigDecimal minStockThreshold);
}
