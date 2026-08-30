package com.optiplant.inventory.pricing.application.port.out;

import com.optiplant.inventory.pricing.domain.model.Price;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for price row persistence (design §5).
 */
public interface PriceRepositoryPort {

	List<Price> findOpen(UUID priceListExternalId, UUID productExternalId, UUID branchExternalId);

	Optional<Price> findByExternalId(UUID externalId);

	Price save(Price price);

	PricePage list(PriceFilter filter);

	List<Price> findEligible(UUID priceListExternalId, UUID branchExternalId,
			Collection<UUID> productExternalIds, LocalDate operationDate);

	record PriceFilter(UUID priceListExternalId, UUID productExternalId, UUID branchExternalId,
			Boolean currentOnly, int page, int size) {
	}

	record PricePage(List<Price> content, long totalElements, int page, int size) {
	}
}
