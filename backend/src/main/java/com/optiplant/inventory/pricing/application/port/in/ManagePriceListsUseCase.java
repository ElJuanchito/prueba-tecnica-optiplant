package com.optiplant.inventory.pricing.application.port.in;

import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort.PriceListPage;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Primary use case for price list administration (RF-VEN-03, design §5).
 * Writes require {@code ADMIN}; reads are open to any authenticated role.
 */
public interface ManagePriceListsUseCase {

	PriceList create(AuthenticatedPrincipal actor, CreatePriceListCommand command);

	PriceList update(AuthenticatedPrincipal actor, UUID externalId, UpdatePriceListCommand command);

	PriceList deactivate(AuthenticatedPrincipal actor, UUID externalId);

	PriceListPage list(AuthenticatedPrincipal actor, PriceListQuery query);

	PriceList get(AuthenticatedPrincipal actor, UUID externalId);

	record CreatePriceListCommand(String code, String name, String description, BigDecimal maxDiscountPercent) {
	}

	record UpdatePriceListCommand(String name, String description, BigDecimal maxDiscountPercent) {
	}

	record PriceListQuery(Boolean active, int page, int size) {
	}
}
