package com.optiplant.inventory.analytics.application.service;

import com.optiplant.inventory.analytics.application.port.in.QueryProductRotationUseCase;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.SalesAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.RotationDirection;
import com.optiplant.inventory.analytics.domain.model.RotationLine;
import com.optiplant.inventory.analytics.domain.service.AnalyticsAccessPolicy;
import com.optiplant.inventory.analytics.domain.service.RotationPageAssembler;
import com.optiplant.inventory.analytics.domain.service.RotationPageAssembler.RawRotationRow;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates product rotation and ABC queries (CU-DSH-01, RF-DSH-02, design §4 Q-2, Q-3, §8).
 *
 * <p>{@code @Service} restored in S2 (design §12 trap 6).
 */
public class QueryProductRotationService implements QueryProductRotationUseCase {

	private final SalesAnalyticsPort salesAnalyticsPort;
	private final BranchDirectoryPort branchDirectoryPort;
	private final Clock clock;

	public QueryProductRotationService(SalesAnalyticsPort salesAnalyticsPort,
			BranchDirectoryPort branchDirectoryPort, Clock clock) {
		this.salesAnalyticsPort = salesAnalyticsPort;
		this.branchDirectoryPort = branchDirectoryPort;
		this.clock = clock;
	}

	public QueryProductRotationService(SalesAnalyticsPort salesAnalyticsPort,
			BranchDirectoryPort branchDirectoryPort) {
		this(salesAnalyticsPort, branchDirectoryPort, Clock.systemUTC());
	}

	@Override
	@Transactional(readOnly = true)
	public AnalyticsPage<RotationLine> rotation(AuthenticatedPrincipal actor, RotationQuery query) {
		UUID targetBranch = AnalyticsAccessPolicy.resolveBranch(actor, query.branchExternalId());
		if (actor.role() == Role.ADMIN && query.branchExternalId() != null) {
			if (!branchDirectoryPort.isActiveBranch(targetBranch)) {
				throw new BranchNotFoundException(targetBranch);
			}
		}

		Instant from = query.from();
		Instant to = query.to();
		if (from == null || to == null) {
			YearMonth currentMonth = YearMonth.now(clock);
			if (from == null) {
				from = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
			if (to == null) {
				to = currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
		}

		if (from.isAfter(to)) {
			throw new IllegalArgumentException("from date cannot be after to date");
		}
		long days = Duration.between(from, to).toDays();
		if (days > 366) {
			throw new IllegalArgumentException("Date range cannot exceed 366 days");
		}
		int periodDays = (int) Math.max(1, days);

		RotationDirection direction = query.direction() != null ? query.direction() : RotationDirection.TOP;
		List<RawRotationRow> rows = salesAnalyticsPort.rotation(targetBranch, from, to, direction,
				query.page(), query.size());
		long totalElements = salesAnalyticsPort.rotationCount(targetBranch, from, to);

		return RotationPageAssembler.assemblePage(rows, totalElements, query.page(), query.size(), periodDays);
	}
}
