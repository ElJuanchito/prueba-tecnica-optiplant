package com.optiplant.inventory.inventory.application.port.out;

import com.optiplant.inventory.inventory.domain.model.ProductDescriptor;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port resolving product descriptors from {@code catalog}'s {@code products} table
 * (design §5.2, §6.1) — named for the need, not for {@code catalog}, since {@code inventory}
 * cannot import that module. No {@code @Entity} spans the boundary; the implementation resolves
 * {@code external_id -> id} through a native query.
 */
public interface ProductLookupPort {

	Optional<ProductDescriptor> findByExternalId(UUID productExternalId);

	/** Batch resolution — avoids one query per row (RNF-PER-01) when enriching a page of balances. */
	Map<UUID, ProductDescriptor> findAllByExternalIds(Collection<UUID> productExternalIds);
}
