package com.optiplant.inventory.purchases.application.service;

import static com.optiplant.inventory.purchases.domain.PurchaseOrderFixtures.item;
import static com.optiplant.inventory.purchases.domain.PurchaseOrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.purchases.application.port.in.ReceivePurchaseUseCase.ReceivePurchaseCommand;
import com.optiplant.inventory.purchases.application.port.in.ReceivePurchaseUseCase.ReceptionItemCommand;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort;
import com.optiplant.inventory.purchases.domain.model.CostHistoryEntry;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderSummary;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import com.optiplant.inventory.shared.stock.InTransitShiftCommand;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Task 1.13 — service behaviour with stubbed ports: {@code applyMovement} is called once per
 * non-zero line <strong>in product {@code external_id} order</strong> with {@code PURCHASE_RECEIPT},
 * the effective unit cost, {@code reference_type = "PURCHASE_ORDER"} and {@code reference_id} = the
 * order's {@code external_id} (R-15), and exactly one audit entry lands on the order's branch
 * (T-01, T-03).
 */
class ReceivePurchaseServiceTest {

	private static final UUID BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
	private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID ITEM_P2 = UUID.fromString("00000000-0000-0000-0000-00000000a001");
	private static final UUID ITEM_P1 = UUID.fromString("00000000-0000-0000-0000-00000000a002");

	private RecordingStockMutationPort stockPort;
	private RecordingAuditPort auditPort;
	private StubOrderRepository orderRepository;
	private ReceivePurchaseService service;
	private AuthenticatedPrincipal manager;

	@BeforeEach
	void setUp() {
		stockPort = new RecordingStockMutationPort();
		auditPort = new RecordingAuditPort();
		orderRepository = new StubOrderRepository();
		service = new ReceivePurchaseService(orderRepository, new EmptyReferencePort(), stockPort, auditPort);
		manager = new AuthenticatedPrincipal(UUID.randomUUID(), "buyer", Role.BRANCH_MANAGER, BRANCH);
	}

	private PurchaseOrder approvedOrderWithTwoLines() {
		// item order is [P2, P1] so the product-sorted lock order [P1, P2] is a real re-ordering
		PurchaseOrderItem lineP2 = item(ITEM_P2, P2, "100", "0", "10", "0");   // effective 10.0000
		PurchaseOrderItem lineP1 = item(ITEM_P1, P1, "100", "0", "20", "10");  // effective 18.0000
		return order(BRANCH, PurchaseOrderStatus.APPROVED, lineP2, lineP1);
	}

	@Test
	@DisplayName("R-15: applyMovement once per non-zero line, in product order, with the effective cost")
	void appliesOneMovementPerNonZeroLineInProductOrder() {
		PurchaseOrder order = approvedOrderWithTwoLines();
		orderRepository.locked = order;

		service.receive(manager, order.externalId(), new ReceivePurchaseCommand(null, List.of(
				new ReceptionItemCommand(ITEM_P2, new BigDecimal("5"), null),
				new ReceptionItemCommand(ITEM_P1, new BigDecimal("3"), null))));

		assertThat(stockPort.commands).hasSize(2);

		StockMutationCommand first = stockPort.commands.get(0);
		assertThat(first.productExternalId()).isEqualTo(P1);
		assertThat(first.movementType()).isEqualTo(StockMovementType.PURCHASE_RECEIPT);
		assertThat(first.quantity()).isEqualByComparingTo("3");
		assertThat(first.unitCost()).isEqualByComparingTo("18.0000");
		assertThat(first.referenceType()).isEqualTo("PURCHASE_ORDER");
		assertThat(first.referenceId()).isEqualTo(order.externalId().toString());
		assertThat(first.branchExternalId()).isEqualTo(BRANCH);
		assertThat(first.actorUserExternalId()).isEqualTo(manager.userId());

		StockMutationCommand second = stockPort.commands.get(1);
		assertThat(second.productExternalId()).isEqualTo(P2);
		assertThat(second.quantity()).isEqualByComparingTo("5");
		assertThat(second.unitCost()).isEqualByComparingTo("10.0000");
	}

	@Test
	@DisplayName("R-22: a zero line drives no applyMovement call")
	void zeroLineDoesNotMoveStock() {
		PurchaseOrder order = approvedOrderWithTwoLines();
		orderRepository.locked = order;

		service.receive(manager, order.externalId(), new ReceivePurchaseCommand(null, List.of(
				new ReceptionItemCommand(ITEM_P2, new BigDecimal("5"), null),
				new ReceptionItemCommand(ITEM_P1, BigDecimal.ZERO, null))));

		assertThat(stockPort.commands).hasSize(1);
		assertThat(stockPort.commands.get(0).productExternalId()).isEqualTo(P2);
	}

	@Test
	@DisplayName("T-01/T-03: exactly one audit entry, on the order's branch")
	void writesOneAuditEntryOnTheOrdersBranch() {
		PurchaseOrder order = approvedOrderWithTwoLines();
		orderRepository.locked = order;

		service.receive(manager, order.externalId(), new ReceivePurchaseCommand(null, List.of(
				new ReceptionItemCommand(ITEM_P2, new BigDecimal("5"), null))));

		assertThat(auditPort.entries).hasSize(1);
		AuditEntryCommand entry = auditPort.entries.get(0);
		assertThat(entry.action()).isEqualTo("RECEIVE_PURCHASE_ORDER");
		assertThat(entry.entityName()).isEqualTo("PURCHASE_ORDER");
		assertThat(entry.entityId()).isEqualTo(order.externalId().toString());
		assertThat(entry.branchId()).isEqualTo(BRANCH);
		assertThat(entry.actorUserId()).isEqualTo(manager.userId());
	}

	// --- stubs -------------------------------------------------------------------------------

	private static final class RecordingStockMutationPort implements StockMutationPort {
		private final List<StockMutationCommand> commands = new ArrayList<>();

		@Override
		public UUID applyMovement(StockMutationCommand command) {
			commands.add(command);
			return UUID.randomUUID();
		}

		@Override
		public void shiftInTransit(InTransitShiftCommand command) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class RecordingAuditPort implements AuditWritePort {
		private final List<AuditEntryCommand> entries = new ArrayList<>();

		@Override
		public void record(AuditEntryCommand command) {
			entries.add(command);
		}
	}

	private static final class StubOrderRepository implements PurchaseOrderRepositoryPort {
		private PurchaseOrder locked;

		@Override
		public Optional<PurchaseOrder> lockForUpdate(UUID externalId) {
			return Optional.ofNullable(locked);
		}

		@Override
		public PurchaseOrder save(PurchaseOrder order) {
			return order;
		}

		@Override
		public PurchaseOrder create(NewPurchaseOrder newOrder) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<PurchaseOrder> findByExternalId(UUID externalId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PurchaseOrder replaceItems(PurchaseOrder order, List<NewPurchaseOrderItem> items,
				com.optiplant.inventory.purchases.domain.model.Money totalAmount) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PurchasePage<PurchaseOrderSummary> list(PurchaseOrderFilter filter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PurchasePage<CostHistoryEntry> costHistory(CostHistoryFilter filter) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class EmptyReferencePort implements PurchaseReferencePort {
		@Override
		public void requireActiveProducts(Collection<UUID> productExternalIds) {
		}

		@Override
		public Map<UUID, ProductDescriptor> findProducts(Collection<UUID> ids) {
			return Map.of();
		}

		@Override
		public Map<UUID, BranchDescriptor> findBranches(Collection<UUID> ids) {
			return Map.of();
		}

		@Override
		public Map<UUID, UserDescriptor> findUsers(Collection<UUID> ids) {
			return Map.of();
		}

		@Override
		public Map<UUID, SupplierDescriptor> findSuppliers(Collection<UUID> ids) {
			return Map.of();
		}

		@Override
		public Map<UUID, BigDecimal> conversionFactors(Collection<ProductUnitRef> productUnits) {
			return Map.of();
		}
	}
}
