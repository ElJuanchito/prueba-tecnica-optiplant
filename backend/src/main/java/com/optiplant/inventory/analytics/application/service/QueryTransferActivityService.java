package com.optiplant.inventory.analytics.application.service;

import com.optiplant.inventory.analytics.application.port.in.QueryTransferActivityUseCase;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.TransferAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.TransferActivitySummary;
import com.optiplant.inventory.analytics.domain.model.TransferStockImpact;
import com.optiplant.inventory.analytics.domain.service.AnalyticsAccessPolicy;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates active-transfer analytics queries (CU-DSH-01, RF-DSH-03, design §4 Q-5, Q-6, §8).
 *
 * <p>{@code @Service} restored in S2 (design §12 trap 6).
 */
public class QueryTransferActivityService implements QueryTransferActivityUseCase {

	private final TransferAnalyticsPort transferAnalyticsPort;
	private final BranchDirectoryPort branchDirectoryPort;

	public QueryTransferActivityService(TransferAnalyticsPort transferAnalyticsPort,
			BranchDirectoryPort branchDirectoryPort) {
		this.transferAnalyticsPort = transferAnalyticsPort;
		this.branchDirectoryPort = branchDirectoryPort;
	}

	@Override
	@Transactional(readOnly = true)
	public TransferActivitySummary summary(AuthenticatedPrincipal actor, UUID branchExternalId) {
		UUID targetBranch = AnalyticsAccessPolicy.resolveBranch(actor, branchExternalId);
		if (actor.role() == Role.ADMIN && branchExternalId != null) {
			if (!branchDirectoryPort.isActiveBranch(targetBranch)) {
				throw new BranchNotFoundException(targetBranch);
			}
		}
		return transferAnalyticsPort.activitySummary(targetBranch);
	}

	@Override
	@Transactional(readOnly = true)
	public AnalyticsPage<TransferStockImpact> stockImpact(AuthenticatedPrincipal actor, StockImpactQuery query) {
		UUID targetBranch = AnalyticsAccessPolicy.resolveBranch(actor, query.branchExternalId());
		if (actor.role() == Role.ADMIN && query.branchExternalId() != null) {
			if (!branchDirectoryPort.isActiveBranch(targetBranch)) {
				throw new BranchNotFoundException(targetBranch);
			}
		}
		return transferAnalyticsPort.stockImpact(targetBranch, query.page(), query.size());
	}
}
