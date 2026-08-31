package com.optiplant.inventory.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.analytics.application.port.in.QueryReplenishmentUseCase.ReplenishmentQuery;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.InventoryAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentLine;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentSeverity;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryReplenishmentServiceTest {

	private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID OTHER_BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	private StubInventoryAnalyticsPort inventoryPort;
	private StubBranchDirectoryPort directoryPort;
	private QueryReplenishmentService service;
	private AuthenticatedPrincipal manager;

	@BeforeEach
	void setUp() {
		inventoryPort = new StubInventoryAnalyticsPort();
		directoryPort = new StubBranchDirectoryPort();
		service = new QueryReplenishmentService(inventoryPort, directoryPort);
		manager = new AuthenticatedPrincipal(UUID.randomUUID(), "mgr", Role.BRANCH_MANAGER, BRANCH_ID);
		directoryPort.activeBranches.add(BRANCH_ID);
	}

	@Test
	@DisplayName("R-15: replenishment delegates to port with resolved session branch")
	void replenishmentDelegatesWithResolvedBranch() {
		AnalyticsPage<ReplenishmentLine> expected = new AnalyticsPage<>(
				List.of(new ReplenishmentLine(UUID.randomUUID(), "SKU-1", "Product 1",
						BigDecimal.ZERO, new BigDecimal("10"), ReplenishmentSeverity.OUT_OF_STOCK,
						BigDecimal.ZERO)),
				1, 0, 20
		);
		inventoryPort.page = expected;

		AnalyticsPage<ReplenishmentLine> result = service.replenishment(manager,
				new ReplenishmentQuery(ReplenishmentSeverity.OUT_OF_STOCK, "severity", null, 0, 20));

		assertThat(result).isEqualTo(expected);
		assertThat(inventoryPort.lastBranch).isEqualTo(BRANCH_ID);
	}

	@Test
	@DisplayName("R-02: non-ADMIN sending branchExternalId throws CrossBranchAccessDeniedException")
	void nonAdminWithBranchThrowsCrossBranchDenied() {
		assertThatThrownBy(() -> service.replenishment(manager,
				new ReplenishmentQuery(null, null, OTHER_BRANCH, 0, 20)))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
	}

	private static class StubInventoryAnalyticsPort implements InventoryAnalyticsPort {
		UUID lastBranch;
		AnalyticsPage<ReplenishmentLine> page;

		@Override
		public AnalyticsPage<ReplenishmentLine> replenishment(UUID branchExternalId,
				ReplenishmentSeverity severity, String sort, int page, int size) {
			this.lastBranch = branchExternalId;
			return this.page;
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
