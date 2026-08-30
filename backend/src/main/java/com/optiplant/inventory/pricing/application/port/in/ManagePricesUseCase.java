package com.optiplant.inventory.pricing.application.port.in;

import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort.PricePage;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Primary use case for price entry management (R-15, R-16, design §5).
 * Writes require {@code ADMIN}; reads are open to any authenticated role.
 */
public interface ManagePricesUseCase {

	Price setPrice(AuthenticatedPrincipal actor, UUID priceListExternalId, SetPriceCommand command);

	Price closePrice(AuthenticatedPrincipal actor, UUID priceExternalId, ClosePriceCommand command);

	PricePage listPrices(AuthenticatedPrincipal actor, UUID priceListExternalId, PriceQuery query);

	record SetPriceCommand(UUID productExternalId, UUID branchExternalId, BigDecimal unitPrice, LocalDate validFrom) {
	}

	record ClosePriceCommand(LocalDate validTo) {
	}

	record PriceQuery(UUID productExternalId, UUID branchExternalId, Boolean currentOnly, int page, int size) {
	}
}
