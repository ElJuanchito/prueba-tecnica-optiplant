package com.optiplant.inventory.pricing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase.ClosePriceCommand;
import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase.SetPriceCommand;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PricingReferencePort;
import com.optiplant.inventory.pricing.domain.model.DiscountCap;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.PriceListCode;
import com.optiplant.inventory.pricing.domain.model.PriceListName;
import com.optiplant.inventory.pricing.domain.model.UnitPrice;
import com.optiplant.inventory.pricing.domain.model.ValidityRange;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagePricesServiceTest {

	@Mock
	private PriceListRepositoryPort priceListRepository;
	@Mock
	private PriceRepositoryPort priceRepository;
	@Mock
	private PricingReferencePort referencePort;
	@Mock
	private AuditWritePort auditWritePort;

	private ManagePricesService service;
	private AuthenticatedPrincipal admin;
	private UUID adminId;
	private UUID listId;
	private UUID productId;
	private UUID branchId;
	private PriceList priceList;

	@BeforeEach
	void setUp() {
		service = new ManagePricesService(priceListRepository, priceRepository, referencePort, auditWritePort);
		adminId = UUID.randomUUID();
		listId = UUID.randomUUID();
		productId = UUID.randomUUID();
		branchId = UUID.randomUUID();
		admin = new AuthenticatedPrincipal(adminId, "admin", Role.ADMIN, null);
		priceList = new PriceList(listId, new PriceListCode("RETAIL"), new PriceListName("Retail"), null,
				DiscountCap.of("10.00"), false, true, Instant.now(), Instant.now());
	}

	@Test
	@DisplayName("R-16 / T-03: Setting corporate price supersedes open price and audits with null branchId")
	void setCorporatePriceSupersedesAndAuditsWithNullBranch() {
		when(priceListRepository.findByExternalId(listId)).thenReturn(Optional.of(priceList));

		Price openPrice = new Price(UUID.randomUUID(), listId, productId, null,
				UnitPrice.of("50.0000"), ValidityRange.open(LocalDate.of(2026, 1, 1)), Instant.now());
		when(priceRepository.findOpen(listId, productId, null)).thenReturn(List.of(openPrice));
		when(priceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		LocalDate newValidFrom = LocalDate.of(2026, 9, 1);
		SetPriceCommand command = new SetPriceCommand(productId, null, new BigDecimal("60.0000"), newValidFrom);

		Price saved = service.setPrice(admin, listId, command);

		assertThat(saved.unitPrice().value()).isEqualByComparingTo("60.0000");
		assertThat(saved.branchExternalId()).isNull();

		verify(auditWritePort).record(new AuditEntryCommand(
				adminId,
				null, // T-03: corporate price has null branch
				"SET_PRICE",
				"price_list_items",
				saved.externalId().toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-16 / T-03: Setting branch-scoped price audits with the priced branchId")
	void setBranchPriceAuditsWithPricedBranch() {
		when(priceListRepository.findByExternalId(listId)).thenReturn(Optional.of(priceList));
		when(priceRepository.findOpen(listId, productId, branchId)).thenReturn(List.of());
		when(priceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		SetPriceCommand command = new SetPriceCommand(productId, branchId, new BigDecimal("55.0000"), LocalDate.of(2026, 9, 1));

		Price saved = service.setPrice(admin, listId, command);

		assertThat(saved.branchExternalId()).isEqualTo(branchId);

		verify(auditWritePort).record(new AuditEntryCommand(
				adminId,
				branchId, // T-03: priced branch for branch-scoped price
				"SET_PRICE",
				"price_list_items",
				saved.externalId().toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-16 / T-03: Closing a price row sets validTo and records audit")
	void closePriceSetsValidToAndAudits() {
		UUID priceId = UUID.randomUUID();
		Price openPrice = new Price(priceId, listId, productId, branchId,
				UnitPrice.of("50.0000"), ValidityRange.open(LocalDate.of(2026, 1, 1)), Instant.now());

		when(priceRepository.findByExternalId(priceId)).thenReturn(Optional.of(openPrice));
		when(priceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		LocalDate validTo = LocalDate.of(2026, 8, 31);
		Price closed = service.closePrice(admin, priceId, new ClosePriceCommand(validTo));

		assertThat(closed.validity().to()).isEqualTo(validTo);

		verify(auditWritePort).record(new AuditEntryCommand(
				adminId,
				branchId,
				"CLOSE_PRICE",
				"price_list_items",
				priceId.toString(),
				null,
				null,
				null
		));
	}
}
