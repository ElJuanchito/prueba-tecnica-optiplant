package com.optiplant.inventory.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleCommand;
import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleItemCommand;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.BranchDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.ProductDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.UserDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.NewSale;
import com.optiplant.inventory.sales.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.InvoiceNumber;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SaleItem;
import com.optiplant.inventory.sales.domain.model.SaleNotes;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.price.AppliedPriceList;
import com.optiplant.inventory.shared.price.PriceResolutionPort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterSaleServiceTest {

	@Mock
	private SaleRepositoryPort saleRepository;
	@Mock
	private SaleReferencePort referencePort;
	@Mock
	private PriceResolutionPort priceResolutionPort;
	@Mock
	private StockMutationPort stockMutationPort;
	@Mock
	private AuditWritePort auditWritePort;

	private RegisterSaleService service;

	private UUID branchId;
	private UUID userId;
	private UUID priceListId;
	private AuthenticatedPrincipal actor;

	@BeforeEach
	void setUp() {
		service = new RegisterSaleService(saleRepository, referencePort, priceResolutionPort, stockMutationPort, auditWritePort);
		branchId = UUID.randomUUID();
		userId = UUID.randomUUID();
		priceListId = UUID.randomUUID();
		actor = new AuthenticatedPrincipal(userId, "cashier", Role.OPERATOR, branchId);
	}

	@Test
	@DisplayName("P-03 / T-01 / T-02: applyMovement called once per line in product order with unitCost = null and audit recorded")
	void registerSaleCallsStockMutationInProductOrderWithNullUnitCost() {
		UUID productA = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID productB = UUID.fromString("00000000-0000-0000-0000-000000000002");

		// Command has B first, then A -> policy must sort A then B
		RegisterSaleCommand command = new RegisterSaleCommand(
				priceListId,
				"Acme Customer",
				null,
				new BigDecimal("10.00"),
				"Test sale",
				List.of(
						new RegisterSaleItemCommand(productB, new BigDecimal("1.0000"), null, BigDecimal.ZERO),
						new RegisterSaleItemCommand(productA, new BigDecimal("2.0000"), null, BigDecimal.ZERO)
				),
				null
		);

		AppliedPriceList appliedList = new AppliedPriceList(priceListId, "RETAIL", new BigDecimal("20.00"));
		when(priceResolutionPort.findActiveListByExternalId(priceListId)).thenReturn(Optional.of(appliedList));
		when(priceResolutionPort.resolveUnitPrices(any(), any(), any(), any()))
				.thenReturn(Map.of(productA, new BigDecimal("100.0000"), productB, new BigDecimal("50.0000")));

		UUID createdSaleId = UUID.randomUUID();
		when(saleRepository.create(any())).thenAnswer(inv -> {
			NewSale ns = inv.getArgument(0);
			List<SaleItem> items = ns.items().stream()
					.map(ni -> new SaleItem(UUID.randomUUID(), ni.productExternalId(), ni.quantity(), ni.listUnitPrice(), ni.unitPrice(), ni.discountPercent(), ni.subtotal()))
					.toList();
			return new Sale(createdSaleId, InvoiceNumber.of("VEN-2026-0001"), SaleStatus.COMPLETED,
					ns.branchExternalId(), ns.soldByUserExternalId(), ns.priceListExternalId(),
					ns.customerName(), ns.customerTaxId(), ns.totals(), ns.notes(), Instant.now(), items);
		});

		lenient().when(referencePort.findBranches(any())).thenReturn(Map.of(branchId, new BranchDescriptor(branchId, "Main Branch")));
		lenient().when(referencePort.findUsers(any())).thenReturn(Map.of(userId, new UserDescriptor(userId, "cashier")));
		lenient().when(priceResolutionPort.describeLists(any())).thenReturn(Map.of(priceListId, appliedList));
		lenient().when(referencePort.findProducts(any())).thenReturn(Map.of(
				productA, new ProductDescriptor(productA, "SKU-A", "Product A"),
				productB, new ProductDescriptor(productB, "SKU-B", "Product B")
		));

		SaleDetail detail = service.register(actor, command);

		assertThat(detail).isNotNull();
		assertThat(detail.externalId()).isEqualTo(createdSaleId);

		// Verify stock mutation calls in order of productExternalId (productA then productB)
		ArgumentCaptor<StockMutationCommand> captor = ArgumentCaptor.forClass(StockMutationCommand.class);
		verify(stockMutationPort, org.mockito.Mockito.times(2)).applyMovement(captor.capture());
		List<StockMutationCommand> calls = captor.getAllValues();
		assertThat(calls).hasSize(2);

		StockMutationCommand firstCall = calls.get(0);
		assertThat(firstCall.productExternalId()).isEqualTo(productA);
		assertThat(firstCall.movementType()).isEqualTo(StockMovementType.SALE);
		assertThat(firstCall.unitCost()).isNull(); // P-03: outbound SALE must have null supplied cost
		assertThat(firstCall.quantity()).isEqualByComparingTo("2.0000");

		StockMutationCommand secondCall = calls.get(1);
		assertThat(secondCall.productExternalId()).isEqualTo(productB);
		assertThat(secondCall.movementType()).isEqualTo(StockMovementType.SALE);
		assertThat(secondCall.unitCost()).isNull();
		assertThat(secondCall.quantity()).isEqualByComparingTo("1.0000");

		// Verify audit entry recorded
		verify(auditWritePort).record(new AuditEntryCommand(
				userId,
				branchId,
				"REGISTER_SALE",
				"sales",
				createdSaleId.toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-02: Corporate ADMIN with null branchId gets BranchContextRequiredException")
	void corporateAdminGetsBranchContextRequired() {
		AuthenticatedPrincipal corporateAdmin = new AuthenticatedPrincipal(userId, "admin", Role.ADMIN, null);
		RegisterSaleCommand command = new RegisterSaleCommand(
				priceListId, "Customer", null, null, null,
				List.of(new RegisterSaleItemCommand(UUID.randomUUID(), new BigDecimal("1.0000"), null, null)),
				null
		);

		assertThatThrownBy(() -> service.register(corporateAdmin, command))
				.isInstanceOf(BranchContextRequiredException.class);
	}
}
