package com.optiplant.inventory.analytics.application.port.out;

import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.BranchPerformance;

/**
 * Secondary read port for corporate branch comparative board queries (design §4 Q-7, §8).
 */
public interface BranchBoardPort {

	AnalyticsPage<BranchPerformance> corporateBoard(int year, int month, String sort,
			String direction, int page, int size);
}
