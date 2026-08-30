package com.optiplant.inventory.pricing.application.port.out;

import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.PriceListCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for price list persistence (design §5).
 */
public interface PriceListRepositoryPort {

	PriceList save(PriceList priceList);

	Optional<PriceList> findByExternalId(UUID externalId);

	Optional<PriceList> findByCode(PriceListCode code);

	Optional<PriceList> findActiveDefaultListForBranch(UUID branchExternalId);

	PriceListPage list(PriceListFilter filter);

	Map<UUID, PriceList> findByExternalIds(Collection<UUID> externalIds);

	record PriceListFilter(Boolean active, int page, int size) {
	}

	record PriceListPage(List<PriceList> content, long totalElements, int page, int size) {
	}
}
