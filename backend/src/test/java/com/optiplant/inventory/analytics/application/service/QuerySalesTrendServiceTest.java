package com.optiplant.inventory.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.analytics.application.port.in.QuerySalesTrendUseCase.SalesTrendQuery;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.SalesAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.analytics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.analytics.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.RotationDirection;
import com.optiplant.inventory.analytics.domain.model.SalesTrend;
import com.optiplant.inventory.analytics.domain.service.RotationPageAssembler.RawRotationRow;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuerySalesTrendServiceTest {

	private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID OTHER_BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	private StubSalesAnalyticsPort salesPort;
	private StubBranchDirectoryPort directoryPort;
	private QuerySalesTrendService service;
	private AuthenticatedPrincipal manager;
	private AuthenticatedPrincipal corporateAdmin;

	private static final Instant FIXED_NOW = Instant.parse("2026-08-31T00:00:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

	@BeforeEach
	void setUp() {
		salesPort = new StubSalesAnalyticsPort();
		directoryPort = new StubBranchDirectoryPort();
		service = new QuerySalesTrendService(salesPort, directoryPort, FIXED_CLOCK);
		manager = new AuthenticatedPrincipal(UUID.randomUUID(), "mgr", Role.BRANCH_MANAGER, BRANCH_ID);
		corporateAdmin = new AuthenticatedPrincipal(UUID.randomUUID(), "adm", Role.ADMIN, null);
		directoryPort.activeBranches.add(BRANCH_ID);
	}

	@Test
	@DisplayName("R-04: defaults to 4 months window when months is null")
	void defaultsToFourMonths() {
		salesPort.monthlySales = List.of(
				new MonthlySales(2026, 8, 10L, new BigDecimal("20.00"), new BigDecimal("1000.00"))
		);

		SalesTrend trend = service.salesTrend(manager, new SalesTrendQuery(null, null));

		assertThat(trend.months()).hasSize(4);
		assertThat(salesPort.lastBranch).isEqualTo(BRANCH_ID);
	}

	@Test
	@DisplayName("R-07: months outside 1..12 throws IllegalArgumentException")
	void invalidMonthsThrowsException() {
		assertThatThrownBy(() -> service.salesTrend(manager, new SalesTrendQuery(0, null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.salesTrend(manager, new SalesTrendQuery(13, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-02: corporate ADMIN omitting branchExternalId throws BranchContextRequiredException")
	void corporateAdminOmittingBranchThrowsContextRequired() {
		assertThatThrownBy(() -> service.salesTrend(corporateAdmin, new SalesTrendQuery(4, null)))
				.isInstanceOf(BranchContextRequiredException.class);
	}

	@Test
	@DisplayName("R-02: non-ADMIN sending branchExternalId throws CrossBranchAccessDeniedException")
	void nonAdminWithBranchThrowsCrossBranchDenied() {
		assertThatThrownBy(() -> service.salesTrend(manager, new SalesTrendQuery(4, OTHER_BRANCH)))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
	}

	@Test
	@DisplayName("R-02: ADMIN with non-existent branch throws BranchNotFoundException")
	void adminWithNonExistentBranchThrowsNotFound() {
		assertThatThrownBy(() -> service.salesTrend(corporateAdmin, new SalesTrendQuery(4, OTHER_BRANCH)))
				.isInstanceOf(BranchNotFoundException.class);
	}

	private static class StubSalesAnalyticsPort implements SalesAnalyticsPort {
		UUID lastBranch;
		List<MonthlySales> monthlySales = List.of();

		@Override
		public List<MonthlySales> monthlySales(UUID branchExternalId, Instant from, Instant to) {
			this.lastBranch = branchExternalId;
			return monthlySales;
		}

		@Override
		public List<RawRotationRow> rotation(UUID branchExternalId, Instant from, Instant to,
				RotationDirection direction, int page, int size) {
			return List.of();
		}

		@Override
		public long rotationCount(UUID branchExternalId, Instant from, Instant to) {
			return 0;
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
