package com.optiplant.inventory.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.analytics.application.port.in.QueryCorporateBoardUseCase.CorporateBoardQuery;
import com.optiplant.inventory.analytics.application.port.out.BranchBoardPort;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.BranchPerformance;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryCorporateBoardServiceTest {

	private StubBranchBoardPort boardPort;
	private QueryCorporateBoardService service;

	private static final Instant FIXED_NOW = Instant.parse("2026-08-31T00:00:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

	@BeforeEach
	void setUp() {
		boardPort = new StubBranchBoardPort();
		service = new QueryCorporateBoardService(boardPort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("R-20: corporateBoard defaults to current year and month when not supplied")
	void defaultsToCurrentYearMonth() {
		AnalyticsPage<BranchPerformance> expected = new AnalyticsPage<>(
				List.of(new BranchPerformance(UUID.randomUUID(), "B01", "Branch 1",
						new BigDecimal("10000.00"), 100L, new BigDecimal("500.00"),
						new BigDecimal("50000.00"), 2L, 1L)),
				1, 0, 20
		);
		boardPort.page = expected;

		AnalyticsPage<BranchPerformance> result = service.corporateBoard(
				new CorporateBoardQuery(null, null, "salesAmount", "DESC", 0, 20));

		assertThat(result).isEqualTo(expected);
		assertThat(boardPort.lastYear).isEqualTo(2026);
		assertThat(boardPort.lastMonth).isEqualTo(8);
	}

	private static class StubBranchBoardPort implements BranchBoardPort {
		int lastYear;
		int lastMonth;
		AnalyticsPage<BranchPerformance> page;

		@Override
		public AnalyticsPage<BranchPerformance> corporateBoard(int year, int month, String sort,
				String direction, int page, int size) {
			this.lastYear = year;
			this.lastMonth = month;
			return this.page;
		}
	}
}
