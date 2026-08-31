package com.optiplant.inventory.analytics.application.port.out;

import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.TransferActivitySummary;
import com.optiplant.inventory.analytics.domain.model.TransferStockImpact;
import java.util.UUID;

/**
 * Secondary read port for active-transfer analytics queries (design §4 Q-5, Q-6, §8).
 */
public interface TransferAnalyticsPort {

	TransferActivitySummary activitySummary(UUID branchExternalId);

	AnalyticsPage<TransferStockImpact> stockImpact(UUID branchExternalId, int page, int size);
}
