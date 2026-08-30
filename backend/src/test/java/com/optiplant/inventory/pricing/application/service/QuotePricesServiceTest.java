package com.optiplant.inventory.pricing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase.QuoteCommand;
import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase.QuoteItemCommand;
import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase.QuoteResult;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PricingReferencePort;
import com.optiplant.inventory.pricing.domain.exception.DiscountCapExceededException;
import com.optiplant.inventory.pricing.domain.model.DiscountCap;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.PriceListCode;
import com.optiplant.inventory.pricing.domain.model.PriceListName;
import com.optiplant.inventory.pricing.domain.model.UnitPrice;
import com.optiplant.inventory.pricing.domain.model.ValidityRange;
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
class QuotePricesServiceTest {

	@Mock
	private PriceListRepositoryPort priceListRepository;
	@Mock
	private PriceRepositoryPort priceRepository;
	@Mock
	private PricingReferencePort referencePort;

	private QuotePricesService service;
	private UUID listId;
	private UUID branchId;
	private UUID productId;
	private AuthenticatedPrincipal actor;
	private PriceList priceList;

	@BeforeEach
	void setUp() {
		service = new QuotePricesService(priceListRepository, priceRepository, referencePort);
		listId = UUID.randomUUID();
		branchId = UUID.randomUUID();
		productId = UUID.randomUUID();
		actor = new AuthenticatedPrincipal(UUID.randomUUID(), "seller", Role.OPERATOR, branchId);
		priceList = new PriceList(listId, new PriceListCode("RETAIL"), new PriceListName("Retail"), null,
				DiscountCap.of("15.00"), false, true, Instant.now(), Instant.now());
	}

	@Test
	@DisplayName("CU-VEN-02: Quote calculates line items and subtotal after discount within cap")
	void quoteCalculatesLineItems() {
		when(priceListRepository.findByExternalId(listId)).thenReturn(Optional.of(priceList));

		Price price = new Price(UUID.randomUUID(), listId, productId, null,
				UnitPrice.of("100.0000"), ValidityRange.open(LocalDate.of(2026, 1, 1)), Instant.now());
		when(priceRepository.findEligible(any(), any(), any(), any())).thenReturn(List.of(price));

		QuoteCommand command = new QuoteCommand(
				listId,
				List.of(new QuoteItemCommand(productId, new BigDecimal("2.0000"), new BigDecimal("10.00")))
		);

		QuoteResult result = service.quote(actor, command);

		assertThat(result.priceListExternalId()).isEqualTo(listId);
		assertThat(result.code()).isEqualTo("RETAIL");
		assertThat(result.maxDiscountPercent()).isEqualByComparingTo("15.00");
		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).listUnitPrice()).isEqualByComparingTo("100.0000");
		assertThat(result.items().get(0).unitPrice()).isEqualByComparingTo("90.0000");
		assertThat(result.items().get(0).subtotal()).isEqualByComparingTo("180.0000");
	}

	@Test
	@DisplayName("CU-VEN-02: Quote with discount exceeding cap throws DiscountCapExceededException")
	void quoteWithDiscountAboveCapThrows() {
		when(priceListRepository.findByExternalId(listId)).thenReturn(Optional.of(priceList));

		Price price = new Price(UUID.randomUUID(), listId, productId, null,
				UnitPrice.of("100.0000"), ValidityRange.open(LocalDate.of(2026, 1, 1)), Instant.now());
		when(priceRepository.findEligible(any(), any(), any(), any())).thenReturn(List.of(price));

		QuoteCommand command = new QuoteCommand(
				listId,
				List.of(new QuoteItemCommand(productId, new BigDecimal("1.0000"), new BigDecimal("20.00")))
		);

		assertThatThrownBy(() -> service.quote(actor, command))
				.isInstanceOf(DiscountCapExceededException.class);
	}
}
