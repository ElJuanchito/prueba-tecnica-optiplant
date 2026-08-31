package com.optiplant.inventory.analytics.application.port.out;

import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentLine;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentSeverity;
import java.util.UUID;

/**
 * Secondary read port for inventory replenishment queries (design §4 Q-4, §8).
 */
public interface InventoryAnalyticsPort {

	AnalyticsPage<ReplenishmentLine> replenishment(UUID branchExternalId,
			ReplenishmentSeverity severity, String sort, int page, int size);
}
