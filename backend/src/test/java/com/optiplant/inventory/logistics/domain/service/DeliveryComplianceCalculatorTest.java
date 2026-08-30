package com.optiplant.inventory.logistics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.logistics.domain.model.ComplianceRow;
import com.optiplant.inventory.logistics.domain.model.DeliveryOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeliveryComplianceCalculator} (R-26, R-27, D-6): unmeasured deliveries
 * are excluded from the percentage and counted separately, never scored as late;
 * {@code onTimePercentage} is {@code null}, never {@code 0}, when nothing is measurable.
 */
class DeliveryComplianceCalculatorTest {

	private static final UUID ORIGIN = UUID.randomUUID();
	private static final UUID DESTINATION = UUID.randomUUID();
	private static final Instant ESTIMATED = Instant.parse("2026-08-28T12:00:00Z");

	@Test
	void unmeasuredDeliveriesAreExcludedFromThePercentageButCountedSeparately() {
		DeliveryOutcome measuredOnTime = new DeliveryOutcome(ORIGIN, DESTINATION, ESTIMATED, ESTIMATED);
		DeliveryOutcome unmeasured = new DeliveryOutcome(ORIGIN, DESTINATION, null, ESTIMATED.plusSeconds(3600));

		ComplianceRow row = DeliveryComplianceCalculator.compute("route-1", "route-1",
				List.of(measuredOnTime, unmeasured));

		assertThat(row.deliveredCount()).isEqualTo(2);
		assertThat(row.unmeasuredCount()).isEqualTo(1);
		assertThat(row.onTimeCount()).isEqualTo(1);
		assertThat(row.onTimePercentage()).isEqualByComparingTo("100.00");
	}

	@Test
	void onTimePercentageIsNullNeverZeroWhenNothingIsMeasurable() {
		DeliveryOutcome unmeasured = new DeliveryOutcome(ORIGIN, DESTINATION, null, ESTIMATED);

		ComplianceRow row = DeliveryComplianceCalculator.compute("route-1", "route-1", List.of(unmeasured));

		assertThat(row.onTimePercentage()).isNull();
		assertThat(row.averageDeviationHours()).isNull();
		assertThat(row.unmeasuredCount()).isEqualTo(1);
		assertThat(row.deliveredCount()).isEqualTo(1);
	}

	@Test
	void aDeliveryArrivingExactlyOnTimeCountsAsOnTime() {
		DeliveryOutcome exactlyOnTime = new DeliveryOutcome(ORIGIN, DESTINATION, ESTIMATED, ESTIMATED);

		ComplianceRow row = DeliveryComplianceCalculator.compute("route-1", "route-1", List.of(exactlyOnTime));

		assertThat(row.onTimeCount()).isEqualTo(1);
		assertThat(row.onTimePercentage()).isEqualByComparingTo("100.00");
		assertThat(row.averageDeviationHours()).isEqualByComparingTo("0.00");
	}

	@Test
	void aLateDeliveryIsExcludedFromOnTimeAndDeviationIsPositive() {
		DeliveryOutcome late = new DeliveryOutcome(ORIGIN, DESTINATION, ESTIMATED, ESTIMATED.plusSeconds(7200));

		ComplianceRow row = DeliveryComplianceCalculator.compute("route-1", "route-1", List.of(late));

		assertThat(row.onTimeCount()).isEqualTo(0);
		assertThat(row.onTimePercentage()).isEqualByComparingTo("0.00");
		assertThat(row.averageDeviationHours()).isEqualByComparingTo("2.00");
	}

	@Test
	void anEarlyDeliveryYieldsANegativeAverageDeviation() {
		DeliveryOutcome early = new DeliveryOutcome(ORIGIN, DESTINATION, ESTIMATED, ESTIMATED.minusSeconds(3600));

		ComplianceRow row = DeliveryComplianceCalculator.compute("route-1", "route-1", List.of(early));

		assertThat(row.onTimeCount()).isEqualTo(1);
		assertThat(row.averageDeviationHours()).isEqualByComparingTo("-1.00");
	}

	@Test
	void mixedOnTimeAndLateDeliveriesComputeAHalfPercentage() {
		DeliveryOutcome onTime = new DeliveryOutcome(ORIGIN, DESTINATION, ESTIMATED, ESTIMATED);
		DeliveryOutcome late = new DeliveryOutcome(ORIGIN, DESTINATION, ESTIMATED, ESTIMATED.plusSeconds(3600));

		ComplianceRow row = DeliveryComplianceCalculator.compute("route-1", "route-1", List.of(onTime, late));

		assertThat(row.onTimePercentage()).isEqualByComparingTo("50.00");
	}

	@Test
	void anEmptyListYieldsAllZerosAndNullPercentage() {
		ComplianceRow row = DeliveryComplianceCalculator.compute("route-1", "route-1", List.of());

		assertThat(row.deliveredCount()).isZero();
		assertThat(row.unmeasuredCount()).isZero();
		assertThat(row.onTimePercentage()).isNull();
		assertThat(row.averageDeviationHours()).isNull();
	}
}
