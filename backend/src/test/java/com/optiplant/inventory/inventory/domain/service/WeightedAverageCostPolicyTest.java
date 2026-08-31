package com.optiplant.inventory.inventory.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WeightedAverageCostPolicy} — RN-10 / R-18. Covers the HU-INV-03 worked
 * example, the zero-prior-balance branch and the scale-4 rounding.
 */
class WeightedAverageCostPolicyTest {

	private static UnitCost cost(String value) {
		return new UnitCost(new BigDecimal(value));
	}

	private static Quantity qty(String value) {
		return new Quantity(new BigDecimal(value));
	}

	@Test
	@DisplayName("R-18: 100 @ 10 receiving 100 @ 20 yields exactly 15 (HU-INV-03)")
	void huInv03WorkedExample() {
		UnitCost result = WeightedAverageCostPolicy.recalculate(
				new BigDecimal("100"), cost("10"), qty("100"), cost("20"));

		assertThat(result.value()).isEqualByComparingTo("15.0000");
	}

	@Test
	@DisplayName("R-18: a zero prior balance yields the received cost")
	void zeroPriorBalanceYieldsReceivedCost() {
		UnitCost result = WeightedAverageCostPolicy.recalculate(
				BigDecimal.ZERO, cost("7.5000"), qty("40"), cost("12.3456"));

		assertThat(result.value()).isEqualByComparingTo("12.3456");
	}

	@Test
	@DisplayName("R-18: a negative prior balance is treated as the zero-balance branch")
	void negativePriorBalanceYieldsReceivedCost() {
		UnitCost result = WeightedAverageCostPolicy.recalculate(
				new BigDecimal("-3"), cost("9"), qty("10"), cost("4"));

		assertThat(result.value()).isEqualByComparingTo("4.0000");
	}

	@Test
	@DisplayName("D-2: a fractional case is rounded HALF_UP to scale 4")
	void fractionalCaseIsRoundedToScaleFour() {
		// (10 * 3.0000) + (3 * 5.0000) = 45 ; 45 / 13 = 3.461538... -> 3.4615
		UnitCost result = WeightedAverageCostPolicy.recalculate(
				new BigDecimal("10"), cost("3"), qty("3"), cost("5"));

		assertThat(result.value()).isEqualByComparingTo("3.4615");
	}

	@Test
	@DisplayName("D-2: intermediate scale-8 division avoids double rounding")
	void intermediateScaleAvoidsDoubleRounding() {
		// (1 * 1.0000) + (2 * 2.0000) = 5 ; 5 / 3 = 1.66666667 -> UnitCost scale 4 -> 1.6667
		UnitCost result = WeightedAverageCostPolicy.recalculate(
				BigDecimal.ONE, cost("1"), qty("2"), cost("2"));

		assertThat(result.value()).isEqualByComparingTo("1.6667");
	}
}
