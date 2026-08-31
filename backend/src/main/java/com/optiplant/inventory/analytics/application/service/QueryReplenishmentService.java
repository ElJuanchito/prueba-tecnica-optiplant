package com.optiplant.inventory.analytics.application.service;

import com.optiplant.inventory.analytics.application.port.in.QueryReplenishmentUseCase;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.InventoryAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentLine;
import com.optiplant.inventory.analytics.domain.service.AnalyticsAccessPolicy;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates critical replenishment queries (CU-DSH-02, RF-DSH-04, design §4 Q-4, §8).
 *
 * <p>{@code @Service} restored in S2 (design §12 trap 6).
 */
public class QueryReplenishmentService implements QueryReplenishmentUseCase {

	private final InventoryAnalyticsPort inventoryAnalyticsPort;
	private final BranchDirectoryPort branchDirectoryPort;

	public QueryReplenishmentService(InventoryAnalyticsPort inventoryAnalyticsPort,
			BranchDirectoryPort branchDirectoryPort) {
		this.inventoryAnalyticsPort = inventoryAnalyticsPort;
		this.branchDirectoryPort = branchDirectoryPort;
	}

	@Override
	@Transactional(readOnly = true)
	public AnalyticsPage<ReplenishmentLine> replenishment(AuthenticatedPrincipal actor, ReplenishmentQuery query) {
		UUID targetBranch = AnalyticsAccessPolicy.resolveBranch(actor, query.branchExternalId());
		if (actor.role() == Role.ADMIN && query.branchExternalId() != null) {
			if (!branchDirectoryPort.isActiveBranch(targetBranch)) {
				throw new BranchNotFoundException(targetBranch);
			}
		}
		return inventoryAnalyticsPort.replenishment(targetBranch, query.severity(), query.sort(),
				query.page(), query.size());
	}
}
