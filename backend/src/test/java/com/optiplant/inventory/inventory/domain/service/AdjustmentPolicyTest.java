package com.optiplant.inventory.inventory.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.inventory.domain.exception.AdjustmentWithoutDifferenceException;
import com.optiplant.inventory.inventory.domain.service.AdjustmentPolicy.AdjustmentDecision;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AdjustmentPolicy} (R-06, R-08). */
class AdjustmentPolicyTest {

	@Test
	void balanceOneHundredCountNinetyTwoYieldsAdjustmentNegOfEight() {
		AdjustmentDecision decision = AdjustmentPolicy.decide(new BigDecimal("100"), new BigDecimal("92"));

		assertThat(decision.movementType()).isEqualTo(StockMovementType.ADJUSTMENT_NEG);
		assertThat(decision.quantity().value()).isEqualByComparingTo("8.0000");
	}

	@Test
	void aHigherCountYieldsAdjustmentPos() {
		AdjustmentDecision decision = AdjustmentPolicy.decide(new BigDecimal("100"), new BigDecimal("110"));

		assertThat(decision.movementType()).isEqualTo(StockMovementType.ADJUSTMENT_POS);
		assertThat(decision.quantity().value()).isEqualByComparingTo("10.0000");
	}

	@Test
	void anEqualCountIsRefusedAsANoOp() {
		assertThatThrownBy(() -> AdjustmentPolicy.decide(new BigDecimal("100"), new BigDecimal("100")))
				.isInstanceOf(AdjustmentWithoutDifferenceException.class);
	}

	@Test
	void aNegativeCountIsRefusedBeforeAnyComparison() {
		assertThatThrownBy(() -> AdjustmentPolicy.decide(new BigDecimal("100"), new BigDecimal("-1")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aNullCountIsRefused() {
		assertThatThrownBy(() -> AdjustmentPolicy.decide(new BigDecimal("100"), null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
