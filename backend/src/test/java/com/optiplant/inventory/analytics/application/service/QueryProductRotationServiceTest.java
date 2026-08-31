package com.optiplant.inventory.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.analytics.application.port.in.QueryProductRotationUseCase.RotationQuery;
import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import com.optiplant.inventory.analytics.application.port.out.SalesAnalyticsPort;
import com.optiplant.inventory.analytics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.analytics.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.analytics.domain.model.AbcClass;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.RotationDirection;
import com.optiplant.inventory.analytics.domain.model.RotationLine;
import com.optiplant.inventory.analytics.domain.service.RotationPageAssembler.RawRotationRow;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryProductRotationServiceTest {

	private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID OTHER_BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final UUID PROD_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID PROD_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID PROD_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private StubSalesAnalyticsPort salesPort;
	private StubBranchDirectoryPort directoryPort;
	private QueryProductRotationService service;
	private AuthenticatedPrincipal manager;
	private AuthenticatedPrincipal admin;

	private static final Instant FIXED_NOW = Instant.parse("2026-08-31T00:00:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

	@BeforeEach
	void setUp() {
		salesPort = new StubSalesAnalyticsPort();
		directoryPort = new StubBranchDirectoryPort();
		service = new QueryProductRotationService(salesPort, directoryPort, FIXED_CLOCK);
		manager = new AuthenticatedPrincipal(UUID.randomUUID(), "mgr", Role.BRANCH_MANAGER, BRANCH_ID);
		admin = new AuthenticatedPrincipal(UUID.randomUUID(), "adm", Role.ADMIN, null);
		directoryPort.activeBranches.add(BRANCH_ID);
	}

	@Test
	@DisplayName("R-09: abcClass is identical for the same product across different pages (computed over full set)")
	void abcClassStableAcrossPages() {
		// Full dataset has 3 products ranked by sales amount DESC:
		// PROD_1: cumulative share 50% -> A
		// PROD_2: cumulative share 85% -> B
		// PROD_3: cumulative share 100% -> C
		RawRotationRow row1 = new RawRotationRow(PROD_1, "SKU-1", "Prod 1", new BigDecimal("50"), new BigDecimal("5000"),
				new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("100"));
		RawRotationRow row2 = new RawRotationRow(PROD_2, "SKU-2", "Prod 2", new BigDecimal("35"), new BigDecimal("3500"),
				new BigDecimal("35.00"), new BigDecimal("85.00"), new BigDecimal("50"));
		RawRotationRow row3 = new RawRotationRow(PROD_3, "SKU-3", "Prod 3", new BigDecimal("15"), new BigDecimal("1500"),
				new BigDecimal("15.00"), new BigDecimal("100.00"), new BigDecimal("20"));

		salesPort.totalCount = 3;

		// Page 0 (size 2): contains PROD_1, PROD_2
		salesPort.pageRows = List.of(row1, row2);
		AnalyticsPage<RotationLine> page0 = service.rotation(manager,
				new RotationQuery(null, null, RotationDirection.TOP, null, 0, 2));

		assertThat(page0.content()).hasSize(2);
		assertThat(page0.content().get(0).productExternalId()).isEqualTo(PROD_1);
		assertThat(page0.content().get(0).abcClass()).isEqualTo(AbcClass.A);
		assertThat(page0.content().get(1).productExternalId()).isEqualTo(PROD_2);
		assertThat(page0.content().get(1).abcClass()).isEqualTo(AbcClass.B);

		// Page 1 (size 2): contains PROD_3
		salesPort.pageRows = List.of(row3);
		AnalyticsPage<RotationLine> page1 = service.rotation(manager,
				new RotationQuery(null, null, RotationDirection.TOP, null, 1, 2));

		assertThat(page1.content()).hasSize(1);
		assertThat(page1.content().get(0).productExternalId()).isEqualTo(PROD_3);
		assertThat(page1.content().get(0).abcClass()).isEqualTo(AbcClass.C);
	}

	@Test
	@DisplayName("D-6: direction=BOTTOM passes direction to port and preserves abcClass calculation")
	void bottomDirectionPreservesAbcClassification() {
		// When requesting BOTTOM, repository returns lowest demand first (PROD_3, PROD_2, PROD_1),
		// but cumulativeSharePercent was computed by ranking DESC in SQL CTE.
		RawRotationRow row3 = new RawRotationRow(PROD_3, "SKU-3", "Prod 3", new BigDecimal("15"), new BigDecimal("1500"),
				new BigDecimal("15.00"), new BigDecimal("100.00"), new BigDecimal("20"));
		salesPort.totalCount = 3;
		salesPort.pageRows = List.of(row3);

		AnalyticsPage<RotationLine> result = service.rotation(manager,
				new RotationQuery(null, null, RotationDirection.BOTTOM, null, 0, 10));

		assertThat(salesPort.lastDirection).isEqualTo(RotationDirection.BOTTOM);
		assertThat(result.content().get(0).productExternalId()).isEqualTo(PROD_3);
		assertThat(result.content().get(0).abcClass()).isEqualTo(AbcClass.C);
	}

	@Test
	@DisplayName("R-11: from date after to date throws IllegalArgumentException")
	void fromAfterToThrowsException() {
		Instant from = FIXED_NOW;
		Instant to = FIXED_NOW.minus(5, ChronoUnit.DAYS);

		assertThatThrownBy(() -> service.rotation(manager,
				new RotationQuery(from, to, RotationDirection.TOP, null, 0, 20)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-11: window wider than 366 days throws IllegalArgumentException")
	void windowExceeding366DaysThrowsException() {
		Instant from = FIXED_NOW;
		Instant to = FIXED_NOW.plus(370, ChronoUnit.DAYS);

		assertThatThrownBy(() -> service.rotation(manager,
				new RotationQuery(from, to, RotationDirection.TOP, null, 0, 20)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-02: non-ADMIN passing branchExternalId throws CrossBranchAccessDeniedException before any lookup")
	void nonAdminWithBranchThrowsCrossBranchDenied() {
		assertThatThrownBy(() -> service.rotation(manager,
				new RotationQuery(null, null, RotationDirection.TOP, OTHER_BRANCH, 0, 20)))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
		assertThat(salesPort.rotationCalled).isFalse();
	}

	@Test
	@DisplayName("R-02: ADMIN with non-existent or inactive branch throws BranchNotFoundException")
	void adminWithInactiveBranchThrowsBranchNotFound() {
		assertThatThrownBy(() -> service.rotation(admin,
				new RotationQuery(null, null, RotationDirection.TOP, OTHER_BRANCH, 0, 20)))
				.isInstanceOf(BranchNotFoundException.class);
		assertThat(salesPort.rotationCalled).isFalse();
	}

	private static class StubSalesAnalyticsPort implements SalesAnalyticsPort {
		boolean rotationCalled = false;
		RotationDirection lastDirection;
		List<RawRotationRow> pageRows = List.of();
		long totalCount = 0;

		@Override
		public List<MonthlySales> monthlySales(UUID branchExternalId, Instant from, Instant to) {
			return List.of();
		}

		@Override
		public List<RawRotationRow> rotation(UUID branchExternalId, Instant from, Instant to,
				RotationDirection direction, int page, int size) {
			rotationCalled = true;
			lastDirection = direction;
			return pageRows;
		}

		@Override
		public long rotationCount(UUID branchExternalId, Instant from, Instant to) {
			return totalCount;
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
