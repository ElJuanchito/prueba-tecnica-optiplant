package com.optiplant.inventory.inventory.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.StockLevel;
import com.optiplant.inventory.inventory.domain.model.StockThresholdBreach;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AlertRaisingPolicy} — severity by R-20. */
class AlertRaisingPolicyTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private static BranchInventory inventoryWith(String currentStock, String threshold) {
		return new BranchInventory(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				new StockLevel(new BigDecimal(currentStock)), StockLevel.zero(), StockLevel.zero(),
				new StockLevel(new BigDecimal(threshold)), new UnitCost(BigDecimal.ZERO), NOW);
	}

	@Test
	void aboveThresholdRaisesNoBreach() {
		BranchInventory above = inventoryWith("50", "10");

		assertThat(AlertRaisingPolicy.evaluate(above, UUID.randomUUID())).isEmpty();
	}

	@Test
	void exactlyZeroStockIsCritical() {
		BranchInventory zero = inventoryWith("0", "10");

		Optional<StockThresholdBreach> breach = AlertRaisingPolicy.evaluate(zero, UUID.randomUUID());
		assertThat(breach).isPresent();

		OperationalAlertRaised rendered = AlertRaisingPolicy.render(breach.get());
		assertThat(rendered.severity()).isEqualTo(AlertSeverity.CRITICAL);
		assertThat(rendered.alertType()).isEqualTo(AlertType.STOCK_MINIMUM);
	}

	@Test
	void nonZeroBreachIsWarning() {
		BranchInventory belowButNonZero = inventoryWith("5", "10");

		Optional<StockThresholdBreach> breach = AlertRaisingPolicy.evaluate(belowButNonZero, UUID.randomUUID());
		assertThat(breach).isPresent();

		OperationalAlertRaised rendered = AlertRaisingPolicy.render(breach.get());
		assertThat(rendered.severity()).isEqualTo(AlertSeverity.WARNING);
	}

	@Test
	void breachExactlyAtThresholdCounts() {
		BranchInventory atThreshold = inventoryWith("10", "10");

		assertThat(AlertRaisingPolicy.evaluate(atThreshold, UUID.randomUUID())).isPresent();
	}

	@Test
	void subjectTokenIsTheProductExternalId() {
		BranchInventory zero = inventoryWith("0", "10");
		StockThresholdBreach breach = AlertRaisingPolicy.evaluate(zero, UUID.randomUUID()).orElseThrow();

		OperationalAlertRaised rendered = AlertRaisingPolicy.render(breach);
		assertThat(rendered.subjectToken()).isEqualTo(breach.productExternalId().toString());
	}
}
