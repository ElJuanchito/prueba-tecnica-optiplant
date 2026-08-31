package com.optiplant.inventory.analytics.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sales trend over a contiguous window of months for a branch (CU-DSH-01, RF-DSH-01).
 */
public record SalesTrend(UUID branchExternalId, List<MonthlySales> months,
		BigDecimal monthOverMonthVariationPercent, boolean empty) {
}
