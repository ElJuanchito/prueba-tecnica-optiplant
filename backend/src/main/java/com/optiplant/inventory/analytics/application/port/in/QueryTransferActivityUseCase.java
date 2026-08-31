package com.optiplant.inventory.analytics.application.port.in;

import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.TransferActivitySummary;
import com.optiplant.inventory.analytics.domain.model.TransferStockImpact;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Primary use case for active-transfer dashboard and stock impact (CU-DSH-01, RF-DSH-03).
 */
public interface QueryTransferActivityUseCase {

	TransferActivitySummary summary(AuthenticatedPrincipal actor, UUID branchExternalId);

	AnalyticsPage<TransferStockImpact> stockImpact(AuthenticatedPrincipal actor, StockImpactQuery query);

	record StockImpactQuery(UUID branchExternalId, int page, int size) {
	}
}
