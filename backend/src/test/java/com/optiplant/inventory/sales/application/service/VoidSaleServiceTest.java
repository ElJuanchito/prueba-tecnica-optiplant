package com.optiplant.inventory.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.sales.application.port.in.VoidSaleUseCase.VoidSaleCommand;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.BranchDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.ProductDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.UserDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.domain.exception.InvalidSaleStateException;
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
import com.optiplant.inventory.shared.stock.OutboundValuationPort;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoidSaleServiceTest {

	@Mock
	private SaleRepositoryPort saleRepository;
	@Mock
	private SaleReferencePort referencePort;
	@Mock
	private PriceResolutionPort priceResolutionPort;
	@Mock
	private OutboundValuationPort outboundValuationPort;
	@Mock
	private StockMutationPort stockMutationPort;
	@Mock
	private AuditWritePort auditWritePort;

	private VoidSaleService service;

	private UUID branchId;
	private UUID userId;
	private UUID saleId;
	private UUID productId;
	private AuthenticatedPrincipal manager;

	@BeforeEach
	void setUp() {
		service = new VoidSaleService(saleRepository, referencePort, priceResolutionPort,
				outboundValuationPort, stockMutationPort, auditWritePort);
		branchId = UUID.randomUUID();
		userId = UUID.randomUUID();
		saleId = UUID.randomUUID();
		productId = UUID.randomUUID();
		manager = new AuthenticatedPrincipal(userId, "manager", Role.BRANCH_MANAGER, branchId);
	}

	@Test
	@DisplayName("F-1 / F-2 / R-19 / R-21: Void reverses stock via ADJUSTMENT_POS at OutboundValuationPort unit cost and records audit")
	void voidSaleReversesStockAtOriginalCostAndAudits() {
		SaleItem item = new SaleItem(
				UUID.randomUUID(),
				productId,
				SaleQuantity.of("2.0000"),
				Money.of("50.0000"),
				Money.of("50.0000"),
				DiscountPercent.ZERO,
				Money.of("100.0000")
		);
		Sale sale = new Sale(
				saleId,
				InvoiceNumber.of("VEN-2026-0001"),
				SaleStatus.COMPLETED,
				branchId,
				userId,
				UUID.randomUUID(),
				new CustomerName("Customer"),
				null,
				new SaleTotals(Money.of("100.0000"), Money.ZERO, Money.ZERO, Money.of("100.0000")),
				SaleNotes.empty(),
				Instant.now(),
				List.of(item)
		);

		when(saleRepository.lockForUpdate(saleId)).thenReturn(Optional.of(sale));
		when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		BigDecimal originalUnitCost = new BigDecimal("32.5000");
		when(outboundValuationPort.outboundUnitCosts(branchId, "SALE_INVOICE", saleId.toString()))
				.thenReturn(Map.of(productId, originalUnitCost));

		lenient().when(referencePort.findBranches(any())).thenReturn(Map.of(branchId, new BranchDescriptor(branchId, "Branch")));
		lenient().when(referencePort.findUsers(any())).thenReturn(Map.of(userId, new UserDescriptor(userId, "manager")));
		lenient().when(priceResolutionPort.describeLists(any())).thenReturn(Map.of(sale.priceListExternalId(),
				new AppliedPriceList(sale.priceListExternalId(), "RETAIL", new BigDecimal("10.00"))));
		lenient().when(referencePort.findProducts(any())).thenReturn(Map.of(productId, new ProductDescriptor(productId, "SKU", "Prod")));

		SaleDetail detail = service.voidSale(manager, saleId, new VoidSaleCommand("Damaged product returned"));

		assertThat(detail.status()).isEqualTo(SaleStatus.CANCELLED);
		assertThat(detail.cancellationReason()).isEqualTo("Damaged product returned");

		// Verify stock mutation reversal call
		ArgumentCaptor<StockMutationCommand> captor = ArgumentCaptor.forClass(StockMutationCommand.class);
		verify(stockMutationPort).applyMovement(captor.capture());
		StockMutationCommand reversal = captor.getValue();

		assertThat(reversal.branchExternalId()).isEqualTo(branchId);
		assertThat(reversal.productExternalId()).isEqualTo(productId);
		assertThat(reversal.movementType()).isEqualTo(StockMovementType.ADJUSTMENT_POS); // F-1: ADJUSTMENT_POS
		assertThat(reversal.referenceType()).isEqualTo("SALE_VOID");
		assertThat(reversal.referenceId()).isEqualTo(saleId.toString());
		assertThat(reversal.quantity()).isEqualByComparingTo("2.0000");
		assertThat(reversal.unitCost()).isEqualByComparingTo(originalUnitCost); // F-2 / RN-03: valued at original SALE cost

		// Verify audit entry
		verify(auditWritePort).record(new AuditEntryCommand(
				userId,
				branchId,
				"VOID_SALE",
				"sales",
				saleId.toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-18: Voiding an already CANCELLED sale throws InvalidSaleStateException")
	void voidingAlreadyCancelledSaleThrows() {
		SaleItem item = new SaleItem(
				UUID.randomUUID(), productId, SaleQuantity.of("1.0000"),
				Money.of("50.0000"), Money.of("50.0000"), DiscountPercent.ZERO, Money.of("50.0000")
		);
		Sale cancelledSale = new Sale(
				saleId, InvoiceNumber.of("VEN-2026-0001"), SaleStatus.CANCELLED,
				branchId, userId, UUID.randomUUID(), new CustomerName("Customer"), null,
				new SaleTotals(Money.of("50.0000"), Money.ZERO, Money.ZERO, Money.of("50.0000")),
				SaleNotes.empty(), Instant.now(), List.of(item)
		);

		when(saleRepository.lockForUpdate(saleId)).thenReturn(Optional.of(cancelledSale));

		assertThatThrownBy(() -> service.voidSale(manager, saleId, new VoidSaleCommand("Reason")))
				.isInstanceOf(InvalidSaleStateException.class);
	}
}
