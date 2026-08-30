package com.optiplant.inventory.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.sales.application.port.in.QuerySalesUseCase.SaleListQuery;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.BranchDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.ProductDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.UserDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.SaleFilter;
import com.optiplant.inventory.sales.domain.exception.SaleNotFoundException;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.InvoiceNumber;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleAggregates;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SaleItem;
import com.optiplant.inventory.sales.domain.model.SaleNotes;
import com.optiplant.inventory.sales.domain.model.SalePage;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.sales.domain.model.SaleSummary;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import com.optiplant.inventory.shared.price.AppliedPriceList;
import com.optiplant.inventory.shared.price.PriceResolutionPort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
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
class QuerySalesServiceTest {

	@Mock
	private SaleRepositoryPort saleRepository;
	@Mock
	private SaleReferencePort referencePort;
	@Mock
	private PriceResolutionPort priceResolutionPort;

	private QuerySalesService service;
	private UUID branchA;
	private UUID branchB;
	private UUID userId;

	@BeforeEach
	void setUp() {
		service = new QuerySalesService(saleRepository, referencePort, priceResolutionPort);
		branchA = UUID.randomUUID();
		branchB = UUID.randomUUID();
		userId = UUID.randomUUID();
	}

	@Test
	@DisplayName("R-24 / R-25: Listing filters by actor's branch for non-admin, and null for ADMIN")
	void listSalesAppliesBranchFilter() {
		AuthenticatedPrincipal operator = new AuthenticatedPrincipal(userId, "seller", Role.OPERATOR, branchA);
		SaleListQuery query = new SaleListQuery(SaleStatus.COMPLETED, null, null, 0, 20, "createdAt");

		when(saleRepository.list(any())).thenReturn(new SalePage(List.of(), 0, 0, 20, SaleAggregates.empty()));

		service.list(operator, query);

		ArgumentCaptor<SaleFilter> captor = ArgumentCaptor.forClass(SaleFilter.class);
		verify(saleRepository).list(captor.capture());
		assertThat(captor.getValue().callerBranchExternalId()).isEqualTo(branchA);
		assertThat(captor.getValue().customerExternalId()).isNull();

		// Admin query has null callerBranchExternalId
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(userId, "admin", Role.ADMIN, null);
		service.list(admin, query);
		verify(saleRepository, org.mockito.Mockito.times(2)).list(captor.capture());
		assertThat(captor.getValue().callerBranchExternalId()).isNull();
	}

	@Test
	@DisplayName("R-C12 / R-C13: Listing forwards customerExternalId to SaleFilter")
	void listSalesForwardsCustomerFilter() {
		UUID customerId = UUID.randomUUID();
		AuthenticatedPrincipal operator = new AuthenticatedPrincipal(userId, "seller", Role.OPERATOR, branchA);
		SaleListQuery query = new SaleListQuery(SaleStatus.COMPLETED, null, null, 0, 20, "createdAt", customerId);

		when(saleRepository.list(any())).thenReturn(new SalePage(List.of(), 0, 0, 20, SaleAggregates.empty()));

		service.list(operator, query);

		ArgumentCaptor<SaleFilter> captor = ArgumentCaptor.forClass(SaleFilter.class);
		verify(saleRepository).list(captor.capture());
		assertThat(captor.getValue().callerBranchExternalId()).isEqualTo(branchA);
		assertThat(captor.getValue().customerExternalId()).isEqualTo(customerId);
	}

	@Test
	@DisplayName("R-25: Detail lookup for sale belonging to another branch throws SaleNotFoundException")
	void detailOfOtherBranchThrowsNotFound() {
		UUID saleId = UUID.randomUUID();
		Sale saleInBranchB = sampleSale(saleId, branchB);

		when(saleRepository.findByExternalId(saleId)).thenReturn(Optional.of(saleInBranchB));

		AuthenticatedPrincipal operatorBranchA = new AuthenticatedPrincipal(userId, "seller", Role.OPERATOR, branchA);

		assertThatThrownBy(() -> service.detail(operatorBranchA, saleId))
				.isInstanceOf(SaleNotFoundException.class);
	}

	@Test
	@DisplayName("R-23: Detail lookup for own branch returns enriched SaleDetail")
	void detailOfOwnBranchReturnsEnrichedSaleDetail() {
		UUID saleId = UUID.randomUUID();
		Sale saleInBranchA = sampleSale(saleId, branchA);

		when(saleRepository.findByExternalId(saleId)).thenReturn(Optional.of(saleInBranchA));

		lenient().when(referencePort.findBranches(any())).thenReturn(Map.of(branchA, new BranchDescriptor(branchA, "Branch A")));
		lenient().when(referencePort.findUsers(any())).thenReturn(Map.of(userId, new UserDescriptor(userId, "seller")));
		lenient().when(priceResolutionPort.describeLists(any())).thenReturn(Map.of(saleInBranchA.priceListExternalId(),
				new AppliedPriceList(saleInBranchA.priceListExternalId(), "RETAIL", new BigDecimal("10.00"))));
		lenient().when(referencePort.findProducts(any())).thenReturn(Map.of());

		AuthenticatedPrincipal operatorBranchA = new AuthenticatedPrincipal(userId, "seller", Role.OPERATOR, branchA);

		SaleDetail detail = service.detail(operatorBranchA, saleId);

		assertThat(detail).isNotNull();
		assertThat(detail.externalId()).isEqualTo(saleId);
		assertThat(detail.invoiceNumber()).isEqualTo("VEN-2026-0001");
		assertThat(detail.branch().name()).isEqualTo("Branch A");
	}

	private Sale sampleSale(UUID saleId, UUID branchId) {
		SaleItem item = new SaleItem(
				UUID.randomUUID(),
				UUID.randomUUID(),
				SaleQuantity.of("1.0000"),
				Money.of("50.0000"),
				Money.of("50.0000"),
				DiscountPercent.ZERO,
				Money.of("50.0000")
		);
		return new Sale(
				saleId,
				InvoiceNumber.of("VEN-2026-0001"),
				SaleStatus.COMPLETED,
				branchId,
				userId,
				UUID.randomUUID(),
				null,
				new CustomerName("Customer"),
				null,
				new SaleTotals(Money.of("50.0000"), Money.ZERO, Money.ZERO, Money.of("50.0000")),
				SaleNotes.empty(),
				Instant.now(),
				List.of(item)
		);
	}
}
