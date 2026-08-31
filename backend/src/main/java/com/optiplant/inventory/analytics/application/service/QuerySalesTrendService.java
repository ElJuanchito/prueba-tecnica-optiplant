package com.optiplant.inventory.analytics.application.service;

import com.optiplant.inventory.analytics.application.port.in.QuerySalesTrendUseCase;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.SalesAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.SalesTrend;
import com.optiplant.inventory.analytics.domain.service.AnalyticsAccessPolicy;
import com.optiplant.inventory.analytics.domain.service.SalesTrendPolicy;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates sales trend queries (CU-DSH-01, RF-DSH-01, design §4 Q-1, §8).
 *
 * <p>{@code @Service} restored in S2: S1 ships this class unannotated because its out-ports
 * have no adapter yet (design §12 trap 6).
 */
@Service
public class QuerySalesTrendService implements QuerySalesTrendUseCase {

	private final SalesAnalyticsPort salesAnalyticsPort;
	private final BranchDirectoryPort branchDirectoryPort;
	private final Clock clock;

	public QuerySalesTrendService(SalesAnalyticsPort salesAnalyticsPort,
			BranchDirectoryPort branchDirectoryPort, Clock clock) {
		this.salesAnalyticsPort = salesAnalyticsPort;
		this.branchDirectoryPort = branchDirectoryPort;
		this.clock = clock;
	}

	@Autowired
	public QuerySalesTrendService(SalesAnalyticsPort salesAnalyticsPort,
			BranchDirectoryPort branchDirectoryPort) {
		this(salesAnalyticsPort, branchDirectoryPort, Clock.systemUTC());
	}

	@Override
	@Transactional(readOnly = true)
	public SalesTrend salesTrend(AuthenticatedPrincipal actor, SalesTrendQuery query) {
		UUID targetBranch = AnalyticsAccessPolicy.resolveBranch(actor, query.branchExternalId());
		if (actor.role() == Role.ADMIN && query.branchExternalId() != null) {
			if (!branchDirectoryPort.isActiveBranch(targetBranch)) {
				throw new BranchNotFoundException(targetBranch);
			}
		}

		int months = query.months() != null ? query.months() : 4;
		if (months < 1 || months > 12) {
			throw new IllegalArgumentException("Months window must be between 1 and 12");
		}

		YearMonth currentMonth = YearMonth.now(clock);
		YearMonth earliestMonth = currentMonth.minusMonths(months - 1);
		Instant from = earliestMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		List<MonthlySales> raw = salesAnalyticsPort.monthlySales(targetBranch, from, to);
		return SalesTrendPolicy.assemble(targetBranch, currentMonth, months, raw);
	}
}
