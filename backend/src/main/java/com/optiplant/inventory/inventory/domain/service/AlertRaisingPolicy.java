package com.optiplant.inventory.inventory.domain.service;

import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.StockThresholdBreach;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Post-mutation breach evaluation and its rendering into the {@code shared} alert transport
 * (design §3.3, §2.2). {@code shared} is importable by every module — it is not
 * {@code ..application..} or {@code ..infrastructure..} — so this stays pure domain.
 */
public final class AlertRaisingPolicy {

	private AlertRaisingPolicy() {
	}

	/** {@link Optional#empty()} when the updated balance does not breach its threshold. */
	public static Optional<StockThresholdBreach> evaluate(BranchInventory updated, UUID movementExternalId) {
		if (!updated.breachesThreshold()) {
			return Optional.empty();
		}
		return Optional.of(new StockThresholdBreach(updated.branchExternalId(), updated.productExternalId(),
				updated.currentStock().value(), updated.minStockThreshold().value(), movementExternalId));
	}

	/**
	 * Renders a {@link StockThresholdBreach} into the producer-agnostic transport. Severity is
	 * {@link AlertSeverity#CRITICAL} when the resulting stock is exactly zero,
	 * {@link AlertSeverity#WARNING} otherwise (R-20).
	 */
	public static OperationalAlertRaised render(StockThresholdBreach breach) {
		AlertSeverity severity = breach.resultingStock().signum() == 0 ? AlertSeverity.CRITICAL
				: AlertSeverity.WARNING;
		String message = breach.movementExternalId() == null
				? "Stock of product %s in branch %s is %s, at or below the minimum threshold of %s after a threshold update"
						.formatted(breach.productExternalId(), breach.branchExternalId(), breach.resultingStock(),
								breach.threshold())
				: "Stock of product %s in branch %s reached %s, at or below the minimum threshold of %s (movement %s)"
						.formatted(breach.productExternalId(), breach.branchExternalId(), breach.resultingStock(),
								breach.threshold(), breach.movementExternalId());
		return new OperationalAlertRaised(breach.branchExternalId(), AlertType.STOCK_MINIMUM, severity,
				breach.productExternalId().toString(), message, Instant.now());
	}
}
