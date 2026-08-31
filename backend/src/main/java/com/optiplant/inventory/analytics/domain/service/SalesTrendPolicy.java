package com.optiplant.inventory.analytics.domain.service;

import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.SalesTrend;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure function assembling the sales trend window (contract R-04, R-05, R-06, design §4, §5).
 * Handles zero-filling missing months, calculating month-over-month variation (with null for 0 previous sales),
 * and flagging the empty state.
 */
public final class SalesTrendPolicy {

	private SalesTrendPolicy() {
	}

	public static SalesTrend assemble(UUID branchExternalId, YearMonth currentMonth, int monthsCount,
			List<MonthlySales> rawSales) {
		Map<YearMonth, MonthlySales> salesByMonth = new HashMap<>();
		if (rawSales != null) {
			for (MonthlySales sale : rawSales) {
				salesByMonth.put(YearMonth.of(sale.year(), sale.month()), sale);
			}
		}

		List<MonthlySales> assembled = new ArrayList<>(monthsCount);
		for (int i = monthsCount - 1; i >= 0; i--) {
			YearMonth ym = currentMonth.minusMonths(i);
			MonthlySales entry = salesByMonth.get(ym);
			if (entry != null) {
				assembled.add(entry);
			} else {
				assembled.add(new MonthlySales(ym.getYear(), ym.getMonthValue(), 0L,
						BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
						BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
			}
		}

		boolean empty = assembled.stream().allMatch(m -> m.salesCount() == 0L);

		BigDecimal variation = null;
		if (assembled.size() >= 2) {
			MonthlySales current = assembled.get(assembled.size() - 1);
			MonthlySales previous = assembled.get(assembled.size() - 2);

			if (previous.totalAmount() != null && previous.totalAmount().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal currAmount = current.totalAmount() != null ? current.totalAmount() : BigDecimal.ZERO;
				variation = currAmount.subtract(previous.totalAmount())
						.multiply(BigDecimal.valueOf(100))
						.divide(previous.totalAmount(), 2, RoundingMode.HALF_UP);
			}
		}

		return new SalesTrend(branchExternalId, List.copyOf(assembled), variation, empty);
	}
}
