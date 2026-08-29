package com.optiplant.inventory.logistics.application.service;

import com.optiplant.inventory.logistics.application.port.in.ReportComplianceUseCase;
import com.optiplant.inventory.logistics.application.port.out.TransferMonitorReadPort;
import com.optiplant.inventory.logistics.application.port.out.TransferMonitorReadPort.DeliveryFilter;
import com.optiplant.inventory.logistics.domain.model.ComplianceGrouping;
import com.optiplant.inventory.logistics.domain.model.ComplianceRow;
import com.optiplant.inventory.logistics.domain.model.CompliancePage;
import com.optiplant.inventory.logistics.domain.model.DateRange;
import com.optiplant.inventory.logistics.domain.model.DeliveryOutcome;
import com.optiplant.inventory.logistics.domain.service.DeliveryComplianceCalculator;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * On-time compliance reporting (CU-LOG-03, R-26, R-27): folds the deliveries
 * {@link TransferMonitorReadPort#listDeliveries} returns into one {@link ComplianceRow} per
 * route or per destination branch (D-6), then paginates the grouped result.
 *
 * <p>{@code logistics} declares no reference port (design §5.2's three out-ports), so
 * {@code key}/{@code label} carry the raw branch {@code external_id}(s) for this slice; a later
 * slice may enrich them once a lookup becomes available.
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc ({@code transfers} module).
 */
@Service
public class ReportComplianceService implements ReportComplianceUseCase {

	private final TransferMonitorReadPort monitorReadPort;

	public ReportComplianceService(TransferMonitorReadPort monitorReadPort) {
		this.monitorReadPort = monitorReadPort;
	}

	@Override
	@Transactional(readOnly = true)
	public CompliancePage report(AuthenticatedPrincipal actor, ComplianceQuery query) {
		UUID callerBranchExternalId = actor.role() == Role.ADMIN ? null : actor.branchId();
		DateRange range = new DateRange(query.from(), query.to());

		List<DeliveryOutcome> outcomes = monitorReadPort
				.listDeliveries(new DeliveryFilter(callerBranchExternalId, range.from(), range.to()));

		Map<String, List<DeliveryOutcome>> grouped = outcomes.stream()
				.collect(Collectors.groupingBy(outcome -> groupKey(outcome, query.groupBy())));

		List<ComplianceRow> rows = grouped.entrySet().stream()
				.map(entry -> DeliveryComplianceCalculator.compute(entry.getKey(), entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(ComplianceRow::key))
				.toList();

		int fromIndex = Math.min(query.page() * query.size(), rows.size());
		int toIndex = Math.min(fromIndex + query.size(), rows.size());

		return new CompliancePage(rows.subList(fromIndex, toIndex), rows.size(), query.page(), query.size());
	}

	private static String groupKey(DeliveryOutcome outcome, ComplianceGrouping grouping) {
		return grouping == ComplianceGrouping.BRANCH ? outcome.destinationBranchExternalId().toString()
				: outcome.originBranchExternalId() + "->" + outcome.destinationBranchExternalId();
	}
}
