package com.optiplant.inventory.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleCommand;
import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleItemCommand;
import com.optiplant.inventory.sales.application.port.out.CustomerRepositoryPort;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.BranchDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.CustomerDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.ProductDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.UserDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.NewSale;
import com.optiplant.inventory.sales.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.sales.domain.exception.CustomerInactiveException;
import com.optiplant.inventory.sales.domain.exception.CustomerNotFoundException;
import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerContact;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterSaleServiceTest {

	@Mock
	private SaleRepositoryPort saleRepository;
	@Mock
	private CustomerRepositoryPort customerRepository;
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
		service = new RegisterSaleService(saleRepository, customerRepository, referencePort, priceResolutionPort, stockMutationPort, auditWritePort);
		branchId = UUID.randomUUID();
		userId = UUID.randomUUID();
		priceListId = UUID.randomUUID();
		actor = new AuthenticatedPrincipal(userId, "cashier", Role.OPERATOR, branchId);
	}

	@Test
	@DisplayName("P-03 / T-01 / T-02 / R-C10: Walk-in sale calls stock mutation in product order and records audit")
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
				null,
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
					ns.customerExternalId(), ns.customerName(), ns.customerTaxId(), ns.totals(), ns.notes(), Instant.now(), items);
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

		ArgumentCaptor<StockMutationCommand> captor = ArgumentCaptor.forClass(StockMutationCommand.class);
		verify(stockMutationPort, org.mockito.Mockito.times(2)).applyMovement(captor.capture());
		List<StockMutationCommand> calls = captor.getAllValues();
		assertThat(calls).hasSize(2);

		StockMutationCommand firstCall = calls.get(0);
		assertThat(firstCall.productExternalId()).isEqualTo(productA);
		assertThat(firstCall.movementType()).isEqualTo(StockMovementType.SALE);
		assertThat(firstCall.unitCost()).isNull();
		assertThat(firstCall.quantity()).isEqualByComparingTo("2.0000");

		StockMutationCommand secondCall = calls.get(1);
		assertThat(secondCall.productExternalId()).isEqualTo(productB);
		assertThat(secondCall.movementType()).isEqualTo(StockMovementType.SALE);
		assertThat(secondCall.unitCost()).isNull();
		assertThat(secondCall.quantity()).isEqualByComparingTo("1.0000");

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
				null, null
		);

		assertThatThrownBy(() -> service.register(corporateAdmin, command))
				.isInstanceOf(BranchContextRequiredException.class);
	}

	@Test
	@DisplayName("R-C8: Sale with unknown customerExternalId throws CustomerNotFoundException")
	void customerNotFoundThrows() {
		UUID unknownCustomerId = UUID.randomUUID();
		RegisterSaleCommand command = new RegisterSaleCommand(
				priceListId, null, null, null, null,
				List.of(new RegisterSaleItemCommand(UUID.randomUUID(), new BigDecimal("1.0000"), null, null)),
				null, unknownCustomerId
		);

		when(customerRepository.findByExternalId(unknownCustomerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register(actor, command))
				.isInstanceOf(CustomerNotFoundException.class)
				.hasMessageContaining(unknownCustomerId.toString());
	}

	@Test
	@DisplayName("R-C7 / D-4: Sale with inactive customer throws CustomerInactiveException")
	void customerInactiveThrows() {
		UUID inactiveCustomerId = UUID.randomUUID();
		Instant now = Instant.now();
		Customer inactive = new Customer(
				inactiveCustomerId,
				new CustomerName("Disabled Customer"),
				null,
				null,
				false,
				now,
				now
		);

		when(customerRepository.findByExternalId(inactiveCustomerId)).thenReturn(Optional.of(inactive));

		RegisterSaleCommand command = new RegisterSaleCommand(
				priceListId, null, null, null, null,
				List.of(new RegisterSaleItemCommand(UUID.randomUUID(), new BigDecimal("1.0000"), null, null)),
				null, inactiveCustomerId
		);

		assertThatThrownBy(() -> service.register(actor, command))
				.isInstanceOf(CustomerInactiveException.class)
				.hasMessageContaining(inactiveCustomerId.toString());
	}

	@Test
	@DisplayName("R-C9: Walk-in sale with blank customerName and null customerExternalId throws IllegalArgumentException")
	void missingCustomerNameAndExternalIdThrows() {
		RegisterSaleCommand command = new RegisterSaleCommand(
				priceListId, "   ", null, null, null,
				List.of(new RegisterSaleItemCommand(UUID.randomUUID(), new BigDecimal("1.0000"), null, null)),
				null, null
		);

		assertThatThrownBy(() -> service.register(actor, command))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-C9 / T-C1: Sale with customerExternalId snapshots customer record and ignores payload name/taxId")
	void customerSnapshotWinsOverPayload() {
		UUID customerId = UUID.randomUUID();
		Instant now = Instant.now();
		Customer customer = new Customer(
				customerId,
				new CustomerName("Canonical Customer Name"),
				CustomerTaxId.of("J-99999999-9"),
				null,
				true,
				now,
				now
		);
		when(customerRepository.findByExternalId(customerId)).thenReturn(Optional.of(customer));

		UUID product = UUID.randomUUID();
		RegisterSaleCommand command = new RegisterSaleCommand(
				priceListId,
				"Ignored Untrusted Payload Name",
				"Ignored Untrusted Tax Id",
				null,
				null,
				List.of(new RegisterSaleItemCommand(product, new BigDecimal("1.0000"), null, null)),
				null,
				customerId
		);

		AppliedPriceList appliedList = new AppliedPriceList(priceListId, "RETAIL", new BigDecimal("20.00"));
		when(priceResolutionPort.findActiveListByExternalId(priceListId)).thenReturn(Optional.of(appliedList));
		when(priceResolutionPort.resolveUnitPrices(any(), any(), any(), any()))
				.thenReturn(Map.of(product, new BigDecimal("10.0000")));

		when(saleRepository.create(any())).thenAnswer(inv -> {
			NewSale ns = inv.getArgument(0);
			assertThat(ns.customerExternalId()).isEqualTo(customerId);
			assertThat(ns.customerName().value()).isEqualTo("Canonical Customer Name");
			assertThat(ns.customerTaxId().value()).isEqualTo("J-99999999-9");
			List<SaleItem> items = ns.items().stream()
					.map(ni -> new SaleItem(UUID.randomUUID(), ni.productExternalId(), ni.quantity(), ni.listUnitPrice(), ni.unitPrice(), ni.discountPercent(), ni.subtotal()))
					.toList();
			return new Sale(UUID.randomUUID(), InvoiceNumber.of("VEN-2026-0002"), SaleStatus.COMPLETED,
					ns.branchExternalId(), ns.soldByUserExternalId(), ns.priceListExternalId(),
					ns.customerExternalId(), ns.customerName(), ns.customerTaxId(), ns.totals(), ns.notes(), Instant.now(), items);
		});

		lenient().when(referencePort.findBranches(any())).thenReturn(Map.of(branchId, new BranchDescriptor(branchId, "Branch")));
		lenient().when(referencePort.findUsers(any())).thenReturn(Map.of(userId, new UserDescriptor(userId, "cashier")));
		lenient().when(priceResolutionPort.describeLists(any())).thenReturn(Map.of(priceListId, appliedList));
		lenient().when(referencePort.findProducts(any())).thenReturn(Map.of(product, new ProductDescriptor(product, "SKU", "Name")));
		lenient().when(referencePort.findCustomers(any())).thenReturn(Map.of(customerId, new CustomerDescriptor(customerId, "Canonical Customer Name", "J-99999999-9")));

		SaleDetail detail = service.register(actor, command);
		assertThat(detail).isNotNull();
		assertThat(detail.customer()).isNotNull();
		assertThat(detail.customer().externalId()).isEqualTo(customerId);
	}
}
