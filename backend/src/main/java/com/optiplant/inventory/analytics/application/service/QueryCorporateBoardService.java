package com.optiplant.inventory.analytics.application.service;

import com.optiplant.inventory.analytics.application.port.in.QueryCorporateBoardUseCase;
import com.optiplant.inventory.analytics.application.port.out.BranchBoardPort;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.BranchPerformance;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates corporate comparative board queries (CU-DSH-03, RF-DSH-05, design §4 Q-7, §8).
 *
 * <p>{@code @Service} restored in S2 (design §12 trap 6).
 */
@Service
public class QueryCorporateBoardService implements QueryCorporateBoardUseCase {

	private final BranchBoardPort branchBoardPort;
	private final Clock clock;

	public QueryCorporateBoardService(BranchBoardPort branchBoardPort, Clock clock) {
		this.branchBoardPort = branchBoardPort;
		this.clock = clock;
	}

	@Autowired
	public QueryCorporateBoardService(BranchBoardPort branchBoardPort) {
		this(branchBoardPort, Clock.systemUTC());
	}

	@Override
	@Transactional(readOnly = true)
	public AnalyticsPage<BranchPerformance> corporateBoard(CorporateBoardQuery query) {
		YearMonth currentMonth = YearMonth.now(clock);
		int year = query.year() != null ? query.year() : currentMonth.getYear();
		int month = query.month() != null ? query.month() : currentMonth.getMonthValue();

		return branchBoardPort.corporateBoard(year, month, query.sort(), query.direction(),
				query.page(), query.size());
	}
}
