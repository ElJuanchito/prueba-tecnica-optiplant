package com.optiplant.inventory.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.analytics.application.port.in.QueryTransferActivityUseCase.StockImpactQuery;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.TransferAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.TransferActivitySummary;
import com.optiplant.inventory.analytics.domain.model.TransferStatusCounts;
import com.optiplant.inventory.analytics.domain.model.TransferStockImpact;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryTransferActivityServiceTest {

	private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID OTHER_BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	private StubTransferAnalyticsPort transferPort;
	private StubBranchDirectoryPort directoryPort;
	private QueryTransferActivityService service;
	private AuthenticatedPrincipal manager;

	@BeforeEach
	void setUp() {
		transferPort = new StubTransferAnalyticsPort();
		directoryPort = new StubBranchDirectoryPort();
		service = new QueryTransferActivityService(transferPort, directoryPort);
		manager = new AuthenticatedPrincipal(UUID.randomUUID(), "mgr", Role.BRANCH_MANAGER, BRANCH_ID);
		directoryPort.activeBranches.add(BRANCH_ID);
	}

	@Test
	@DisplayName("R-12: summary delegates to port with resolved session branch")
	void summaryDelegatesWithResolvedBranch() {
		TransferActivitySummary expected = new TransferActivitySummary(
				new TransferStatusCounts(1, 2, 3),
				new TransferStatusCounts(4, 5, 6),
				0
		);
		transferPort.summary = expected;

		TransferActivitySummary result = service.summary(manager, null);

		assertThat(result).isEqualTo(expected);
		assertThat(transferPort.lastBranch).isEqualTo(BRANCH_ID);
	}

	@Test
	@DisplayName("R-13: stockImpact delegates to port with resolved session branch and pagination")
	void stockImpactDelegatesWithResolvedBranch() {
		AnalyticsPage<TransferStockImpact> expected = new AnalyticsPage<>(
				List.of(new TransferStockImpact(UUID.randomUUID(), "SKU-1", "Product 1",
						new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("5"),
						new BigDecimal("2"), new BigDecimal("13"))),
				1, 0, 20
		);
		transferPort.stockImpactPage = expected;

		AnalyticsPage<TransferStockImpact> result = service.stockImpact(manager,
				new StockImpactQuery(null, 0, 20));

		assertThat(result).isEqualTo(expected);
		assertThat(transferPort.lastBranch).isEqualTo(BRANCH_ID);
	}

	@Test
	@DisplayName("R-02: non-ADMIN sending branchExternalId throws CrossBranchAccessDeniedException")
	void nonAdminWithBranchThrowsCrossBranchDenied() {
		assertThatThrownBy(() -> service.summary(manager, OTHER_BRANCH))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
		assertThatThrownBy(() -> service.stockImpact(manager, new StockImpactQuery(OTHER_BRANCH, 0, 20)))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
	}

	private static class StubTransferAnalyticsPort implements TransferAnalyticsPort {
		UUID lastBranch;
		TransferActivitySummary summary;
		AnalyticsPage<TransferStockImpact> stockImpactPage;

		@Override
		public TransferActivitySummary activitySummary(UUID branchExternalId) {
			this.lastBranch = branchExternalId;
			return summary;
		}

		@Override
		public AnalyticsPage<TransferStockImpact> stockImpact(UUID branchExternalId, int page, int size) {
			this.lastBranch = branchExternalId;
			return stockImpactPage;
		}
	}

	private static class StubBranchDirectoryPort implements BranchDirectoryPort {
		List<UUID> activeBranches = new ArrayList<>();

		@Override
		public boolean isActiveBranch(UUID branchExternalId) {
			return activeBranches.contains(branchExternalId);
		}
	}
}
