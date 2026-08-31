package com.optiplant.inventory.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.SalesTrend;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SalesTrendPolicyTest {

	private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	@DisplayName("R-04: missing months are zero-filled and ordered oldest first")
	void missingMonthsAreZeroFilled() {
		YearMonth currentMonth = YearMonth.of(2026, 8); // Window: May, June, July, August 2026
		List<MonthlySales> rawSales = List.of(
				new MonthlySales(2026, 6, 5L, new BigDecimal("10.00"), new BigDecimal("500.00")),
				new MonthlySales(2026, 8, 12L, new BigDecimal("30.00"), new BigDecimal("1200.00"))
		);

		SalesTrend trend = SalesTrendPolicy.assemble(BRANCH_ID, currentMonth, 4, rawSales);

		assertThat(trend.branchExternalId()).isEqualTo(BRANCH_ID);
		assertThat(trend.months()).hasSize(4);
		assertThat(trend.empty()).isFalse();

		// May 2026 (zero-filled)
		assertThat(trend.months().get(0).year()).isEqualTo(2026);
		assertThat(trend.months().get(0).month()).isEqualTo(5);
		assertThat(trend.months().get(0).salesCount()).isEqualTo(0L);
		assertThat(trend.months().get(0).totalAmount()).isEqualByComparingTo("0.00");

		// June 2026 (from raw)
		assertThat(trend.months().get(1).month()).isEqualTo(6);
		assertThat(trend.months().get(1).salesCount()).isEqualTo(5L);
		assertThat(trend.months().get(1).totalAmount()).isEqualByComparingTo("500.00");

		// July 2026 (zero-filled)
		assertThat(trend.months().get(2).month()).isEqualTo(7);
		assertThat(trend.months().get(2).salesCount()).isEqualTo(0L);

		// August 2026 (from raw)
		assertThat(trend.months().get(3).month()).isEqualTo(8);
		assertThat(trend.months().get(3).salesCount()).isEqualTo(12L);
	}

	@Test
	@DisplayName("R-05: month-over-month variation percent against immediately previous month")
	void monthOverMonthVariationCalculated() {
		YearMonth currentMonth = YearMonth.of(2026, 8);
		List<MonthlySales> rawSales = List.of(
				new MonthlySales(2026, 7, 10L, new BigDecimal("20.00"), new BigDecimal("1000.00")),
				new MonthlySales(2026, 8, 15L, new BigDecimal("30.00"), new BigDecimal("1500.00"))
		);

		SalesTrend trend = SalesTrendPolicy.assemble(BRANCH_ID, currentMonth, 2, rawSales);

		// (1500 - 1000) / 1000 * 100 = 50.00%
		assertThat(trend.monthOverMonthVariationPercent()).isEqualByComparingTo("50.00");
	}

	@Test
	@DisplayName("R-05: previous month with zero sales gives null variation, never division by zero and never 100")
	void zeroPreviousMonthGivesNullVariation() {
		YearMonth currentMonth = YearMonth.of(2026, 8);
		List<MonthlySales> rawSales = List.of(
				new MonthlySales(2026, 8, 10L, new BigDecimal("20.00"), new BigDecimal("1000.00"))
		); // July 2026 will be zero-filled

		SalesTrend trend = SalesTrendPolicy.assemble(BRANCH_ID, currentMonth, 2, rawSales);

		assertThat(trend.monthOverMonthVariationPercent()).isNull();
	}

	@Test
	@DisplayName("R-06: branch with no sales in window returns empty=true with all zero-filled months")
	void allZeroMonthsReturnsEmptyTrue() {
		YearMonth currentMonth = YearMonth.of(2026, 8);
		SalesTrend trend = SalesTrendPolicy.assemble(BRANCH_ID, currentMonth, 4, List.of());

		assertThat(trend.empty()).isTrue();
		assertThat(trend.months()).hasSize(4);
		assertThat(trend.months()).allMatch(m -> m.salesCount() == 0L && m.totalAmount().compareTo(BigDecimal.ZERO) == 0);
		assertThat(trend.monthOverMonthVariationPercent()).isNull();
	}
}
