package com.optiplant.inventory.analytics.application.port.in;

import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentLine;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentSeverity;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Primary use case for critical replenishment panel (CU-DSH-02, RF-DSH-04).
 */
public interface QueryReplenishmentUseCase {

	AnalyticsPage<ReplenishmentLine> replenishment(AuthenticatedPrincipal actor, ReplenishmentQuery query);

	record ReplenishmentQuery(ReplenishmentSeverity severity, String sort, UUID branchExternalId,
			int page, int size) {
	}
}
