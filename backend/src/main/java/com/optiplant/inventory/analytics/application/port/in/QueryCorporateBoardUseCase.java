package com.optiplant.inventory.analytics.application.port.in;

import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.BranchPerformance;

/**
 * Primary use case for corporate comparative branch board (CU-DSH-03, RF-DSH-05).
 * Restricted to ADMIN at the security matcher level (contract §5, R-19, design §6 D-9).
 */
public interface QueryCorporateBoardUseCase {

	AnalyticsPage<BranchPerformance> corporateBoard(CorporateBoardQuery query);

	record CorporateBoardQuery(Integer year, Integer month, String sort, String direction,
			int page, int size) {
	}
}
