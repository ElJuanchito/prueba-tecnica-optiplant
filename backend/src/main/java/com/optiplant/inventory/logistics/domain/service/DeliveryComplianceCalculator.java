package com.optiplant.inventory.logistics.domain.service;

import com.optiplant.inventory.logistics.domain.model.ComplianceRow;
import com.optiplant.inventory.logistics.domain.model.DeliveryOutcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Folds {@link DeliveryOutcome} rows into one {@link ComplianceRow} (R-26, R-27, design §4, D-6).
 * {@code unmeasuredCount = delivered - measured} is reported separately and never scored;
 * {@code onTimePercentage} is {@code null}, never {@code 0}, when nothing is measurable — a zero
 * would score unmeasured deliveries as 100% late, exactly what R-26 forbids.
 */
public final class DeliveryComplianceCalculator {

	private static final int SCALE = 2;

	private DeliveryComplianceCalculator() {
	}

	public static ComplianceRow compute(String key, String label, List<DeliveryOutcome> outcomes) {
		long delivered = outcomes.size();
		long measured = 0;
		long onTime = 0;
		BigDecimal deviationSum = BigDecimal.ZERO;

		for (DeliveryOutcome outcome : outcomes) {
			if (outcome.estimatedArrivalAt() == null) {
				continue;
			}
			measured++;
			BigDecimal deviationHours = hoursBetween(outcome.estimatedArrivalAt(), outcome.actualArrivalAt());
			deviationSum = deviationSum.add(deviationHours);
			if (!outcome.actualArrivalAt().isAfter(outcome.estimatedArrivalAt())) {
				onTime++;
			}
		}

		long unmeasured = delivered - measured;
		BigDecimal onTimePercentage = measured == 0 ? null
				: BigDecimal.valueOf(onTime).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(measured), SCALE, RoundingMode.HALF_UP);
		BigDecimal averageDeviationHours = measured == 0 ? null
				: deviationSum.divide(BigDecimal.valueOf(measured), SCALE, RoundingMode.HALF_UP);

		return new ComplianceRow(key, label, delivered, onTime, onTimePercentage, averageDeviationHours, unmeasured);
	}

	private static BigDecimal hoursBetween(Instant estimated, Instant actual) {
		BigDecimal seconds = BigDecimal.valueOf(Duration.between(estimated, actual).getSeconds());
		return seconds.divide(BigDecimal.valueOf(3600), 10, RoundingMode.HALF_UP);
	}
}
