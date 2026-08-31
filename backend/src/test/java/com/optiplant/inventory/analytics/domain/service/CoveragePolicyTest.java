package com.optiplant.inventory.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoveragePolicyTest {

	@Test
	@DisplayName("R-10/R-15: zero current stock returns 0.00 coverage days")
	void zeroStockReturnsZero() {
		BigDecimal coverage = CoveragePolicy.calculateCoverageDays(BigDecimal.ZERO, new BigDecimal("10"), 30);
		assertThat(coverage).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("Negative stock returns 0.00 coverage days")
	void negativeStockReturnsZero() {
		BigDecimal coverage = CoveragePolicy.calculateCoverageDays(new BigDecimal("-5"), new BigDecimal("10"), 30);
		assertThat(coverage).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("Null stock returns 0.00 coverage days")
	void nullStockReturnsZero() {
		BigDecimal coverage = CoveragePolicy.calculateCoverageDays(null, new BigDecimal("10"), 30);
		assertThat(coverage).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("R-10/R-15: zero demand (unitsSold = 0) returns null, never infinity")
	void zeroDemandReturnsNull() {
		BigDecimal coverage = CoveragePolicy.calculateCoverageDays(new BigDecimal("10"), BigDecimal.ZERO, 30);
		assertThat(coverage).isNull();
	}

	@Test
	@DisplayName("Null demand returns null")
	void nullDemandReturnsNull() {
		BigDecimal coverage = CoveragePolicy.calculateCoverageDays(new BigDecimal("10"), null, 30);
		assertThat(coverage).isNull();
	}

	@Test
	@DisplayName("Non-positive period days returns null")
	void nonPositivePeriodDaysReturnsNull() {
		BigDecimal coverage = CoveragePolicy.calculateCoverageDays(new BigDecimal("10"), new BigDecimal("20"), 0);
		assertThat(coverage).isNull();
	}

	@Test
	@DisplayName("R-10/R-15: standard coverage days calculation at scale 2")
	void standardCoverageCalculation() {
		// currentStock = 10, unitsSold = 20, periodDays = 30 -> dailyDemand = 20/30 -> coverage = 10 / (20/30) = 15.00
		BigDecimal coverage = CoveragePolicy.calculateCoverageDays(new BigDecimal("10"), new BigDecimal("20"), 30);
		assertThat(coverage).isEqualByComparingTo("15.00");

		// currentStock = 7, unitsSold = 20, periodDays = 30 -> 7 * 30 / 20 = 10.50
		BigDecimal coverage2 = CoveragePolicy.calculateCoverageDays(new BigDecimal("7"), new BigDecimal("20"), 30);
		assertThat(coverage2).isEqualByComparingTo("10.50");
	}
}
