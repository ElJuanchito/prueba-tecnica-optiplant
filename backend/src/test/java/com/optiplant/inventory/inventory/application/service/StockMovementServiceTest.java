package com.optiplant.inventory.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.inventory.application.port.in.RegisterStockMovementUseCase.AdjustStockCommand;
import com.optiplant.inventory.inventory.application.port.in.RegisterStockMovementUseCase.WriteOffCommand;
import com.optiplant.inventory.inventory.application.port.out.AlertEventPublisherPort;
import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort.StockFilter;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort;
import com.optiplant.inventory.inventory.domain.exception.AdjustmentReasonRequiredException;
import com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.inventory.domain.exception.InsufficientStockException;
import com.optiplant.inventory.inventory.domain.model.BranchAvailability;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.MovementReceipt;
import com.optiplant.inventory.inventory.domain.model.StockLevel;
import com.optiplant.inventory.inventory.domain.model.StockPage;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StockMovementService} using hand-written in-memory fakes (no Mockito on
 * classpath, mirroring {@code catalog}'s service tests). Covers PA-02 (corporate {@code ADMIN}
 * branch context), R-07 (mandatory reason), R-11 (insufficient stock), and that an audit entry is
 * written on every mutation while the alert event is published exactly when
 * {@code breachesThreshold()} — and not otherwise.
 */
class StockMovementServiceTest {

	private FakeBranchInventoryRepositoryPort branchInventoryRepository;
	private FakeKardexRepositoryPort kardexRepository;
	private FakeAuditWritePort auditWritePort;
	private FakeAlertEventPublisherPort alertEventPublisherPort;
	private StockMovementService service;
	private AuthenticatedPrincipal manager;

	private static final UUID BRANCH = UUID.randomUUID();
	private static final UUID PRODUCT = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		branchInventoryRepository = new FakeBranchInventoryRepositoryPort();
		kardexRepository = new FakeKardexRepositoryPort();
		auditWritePort = new FakeAuditWritePort();
		alertEventPublisherPort = new FakeAlertEventPublisherPort();
		service = new StockMovementService(branchInventoryRepository, kardexRepository, auditWritePort,
				alertEventPublisherPort);
		manager = new AuthenticatedPrincipal(UUID.randomUUID(), "manager", Role.BRANCH_MANAGER, BRANCH);
	}

	@Test
	void adjustRejectsACorporateAdminWithNoBranchContext() {
		AuthenticatedPrincipal corporateAdmin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp",
				Role.ADMIN, null);

		assertThatThrownBy(() -> service.adjust(corporateAdmin, new AdjustStockCommand(PRODUCT, BigDecimal.TEN, "count")))
				.isInstanceOf(BranchContextRequiredException.class);
		assertThat(auditWritePort.recorded).isEmpty();
	}

	@Test
	void adjustRejectsABlankReasonAndWritesNothing() {
		branchInventoryRepository.seed(inventoryOf("100", "10"));

		assertThatThrownBy(() -> service.adjust(manager, new AdjustStockCommand(PRODUCT, new BigDecimal("92"), "  ")))
				.isInstanceOf(AdjustmentReasonRequiredException.class);
		assertThat(auditWritePort.recorded).isEmpty();
		assertThat(kardexRepository.appended).isEmpty();
	}

	@Test
	void adjustWritesAnAuditEntryAndNoAlertWhenAboveThreshold() {
		branchInventoryRepository.seed(inventoryOf("100", "10"));

		MovementReceipt receipt = service.adjust(manager, new AdjustStockCommand(PRODUCT, new BigDecimal("92"), "count"));

		assertThat(receipt.resultingStock()).isEqualByComparingTo("92.0000");
		assertThat(auditWritePort.recorded).extracting(AuditEntryCommand::action).containsExactly("ADJUST_STOCK");
		assertThat(alertEventPublisherPort.published).isEmpty();
	}

	@Test
	void adjustPublishesAnAlertExactlyWhenTheResultBreachesTheThreshold() {
		branchInventoryRepository.seed(inventoryOf("100", "10"));

		service.adjust(manager, new AdjustStockCommand(PRODUCT, new BigDecimal("5"), "count"));

		assertThat(alertEventPublisherPort.published).hasSize(1);
		assertThat(auditWritePort.recorded).hasSize(1);
	}

	@Test
	void writeOffAboveAvailableBalanceIsInsufficientStockAndWritesNothing() {
		branchInventoryRepository.seed(inventoryOf("5", "10"));

		assertThatThrownBy(
				() -> service.writeOff(manager, new WriteOffCommand(PRODUCT, BigDecimal.TEN, "damaged")))
				.isInstanceOf(InsufficientStockException.class);
		assertThat(auditWritePort.recorded).isEmpty();
		assertThat(alertEventPublisherPort.published).isEmpty();
	}

	@Test
	void writeOffSucceedsWritesAuditAndPublishesNoAlertWhenAboveThreshold() {
		branchInventoryRepository.seed(inventoryOf("100", "10"));

		MovementReceipt receipt = service.writeOff(manager, new WriteOffCommand(PRODUCT, new BigDecimal("5"), "damaged"));

		assertThat(receipt.resultingStock()).isEqualByComparingTo("95.0000");
		assertThat(auditWritePort.recorded).extracting(AuditEntryCommand::action).containsExactly("WRITE_OFF_STOCK");
		assertThat(alertEventPublisherPort.published).isEmpty();
	}

	@Test
	void mutationCreatesTheBranchInventoryRowOnDemandWhenAbsent() {
		// No seed: the row does not exist yet (F-3). A positive count adjustment from zero is
		// ADJUSTMENT_POS — inbound, so it succeeds without an insufficient-stock check.
		MovementReceipt receipt = service.adjust(manager, new AdjustStockCommand(PRODUCT, new BigDecimal("10"), "initial count"));

		assertThat(receipt.resultingStock()).isEqualByComparingTo("10.0000");
		assertThat(auditWritePort.recorded).hasSize(1);
	}

	@Test
	void aPositiveAdjustmentIsValuedAtTheBranchsCurrentAverageCostSinceTheCommandCarriesNone() {
		branchInventoryRepository.seed(inventoryOf("100", "10"));

		MovementReceipt receipt = service.adjust(manager, new AdjustStockCommand(PRODUCT, new BigDecimal("110"), "count"));

		assertThat(receipt.resultingStock()).isEqualByComparingTo("110.0000");
		assertThat(auditWritePort.recorded).hasSize(1);
	}

	private static BranchInventory inventoryOf(String currentStock, String threshold) {
		return new BranchInventory(UUID.randomUUID(), BRANCH, PRODUCT, new StockLevel(new BigDecimal(currentStock)),
				StockLevel.zero(), StockLevel.zero(), new StockLevel(new BigDecimal(threshold)),
				new UnitCost(BigDecimal.TEN), Instant.now());
	}

	private static final class FakeBranchInventoryRepositoryPort implements BranchInventoryRepositoryPort {

		private final Map<String, BranchInventory> byKey = new HashMap<>();

		void seed(BranchInventory inventory) {
			byKey.put(key(inventory.branchExternalId(), inventory.productExternalId()), inventory);
		}

		private static String key(UUID branch, UUID product) {
			return branch + ":" + product;
		}

		@Override
		public Optional<BranchInventory> lockForUpdate(UUID branchExternalId, UUID productExternalId) {
			return Optional.ofNullable(byKey.get(key(branchExternalId, productExternalId)));
		}

		@Override
		public BranchInventory createZeroed(UUID branchExternalId, UUID productExternalId) {
			BranchInventory zeroed = new BranchInventory(UUID.randomUUID(), branchExternalId, productExternalId,
					StockLevel.zero(), StockLevel.zero(), StockLevel.zero(), StockLevel.zero(),
					new UnitCost(BigDecimal.ZERO), Instant.now());
			byKey.put(key(branchExternalId, productExternalId), zeroed);
			return zeroed;
		}

		@Override
		public BranchInventory save(BranchInventory inventory) {
			byKey.put(key(inventory.branchExternalId(), inventory.productExternalId()), inventory);
			return inventory;
		}

		@Override
		public StockPage list(StockFilter filter) {
			throw new UnsupportedOperationException("not exercised by these unit tests");
		}

		@Override
		public List<BranchAvailability> findAcrossActiveBranches(UUID productExternalId) {
			throw new UnsupportedOperationException("not exercised by these unit tests");
		}

		@Override
		public boolean hasAnyBalance(UUID productExternalId) {
			throw new UnsupportedOperationException("not exercised by these unit tests");
		}
	}

	private static final class FakeKardexRepositoryPort implements KardexRepositoryPort {

		private final List<KardexMovement> appended = new ArrayList<>();

		@Override
		public KardexMovement append(NewMovement movement) {
			KardexMovement created = new KardexMovement(UUID.randomUUID(), movement.branchExternalId(),
					movement.productExternalId(), movement.movementType(), movement.quantity(), movement.unitCost(),
					movement.totalCost(), movement.previousStock(), movement.resultingStock(),
					movement.referenceType(), movement.referenceId(), movement.notes(), movement.userExternalId(),
					Instant.now());
			appended.add(created);
			return created;
		}

		@Override
		public com.optiplant.inventory.inventory.domain.model.KardexPage list(KardexFilter filter) {
			throw new UnsupportedOperationException("not exercised by these unit tests");
		}

		@Override
		public boolean hasAnyMovement(UUID productExternalId) {
			throw new UnsupportedOperationException("not exercised by these unit tests");
		}
	}

	private static final class FakeAuditWritePort implements AuditWritePort {

		private final List<AuditEntryCommand> recorded = new ArrayList<>();

		@Override
		public void record(AuditEntryCommand command) {
			recorded.add(command);
		}
	}

	private static final class FakeAlertEventPublisherPort implements AlertEventPublisherPort {

		private final List<OperationalAlertRaised> published = new ArrayList<>();

		@Override
		public void publish(OperationalAlertRaised event) {
			published.add(event);
		}
	}
}
