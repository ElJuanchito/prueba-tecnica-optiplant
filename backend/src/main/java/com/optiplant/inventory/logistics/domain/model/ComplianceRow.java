package com.optiplant.inventory.logistics.domain.model;

import java.math.BigDecimal;

/**
 * One grouped row of the compliance report (RF-LOG-02, RF-LOG-04, contract §6, design §4, D-6).
 *
 * @param onTimePercentage      {@code onTimeCount / (deliveredCount - unmeasuredCount) * 100},
 *                              scale 2, {@code null} when nothing is measurable (R-26) — never
 *                              {@code 0}, which would score unmeasured deliveries as 100% late
 * @param averageDeviationHours the signed mean of {@code actual - estimated} in hours, scale 2,
 *                              negative meaning early; {@code null} when nothing is measurable
 * @param unmeasuredCount       delivered transfers whose ETA was never precomputed (P-11) — never scored
 */
public record ComplianceRow(String key, String label, long deliveredCount, long onTimeCount,
		BigDecimal onTimePercentage, BigDecimal averageDeviationHours, long unmeasuredCount) {
}
