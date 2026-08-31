package com.optiplant.inventory.analytics.application.port.in;

import com.optiplant.inventory.analytics.domain.model.SalesTrend;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Primary use case for sales trend analysis (CU-DSH-01, RF-DSH-01).
 */
public interface QuerySalesTrendUseCase {

	SalesTrend salesTrend(AuthenticatedPrincipal actor, SalesTrendQuery query);

	record SalesTrendQuery(Integer months, UUID branchExternalId) {
	}
}
