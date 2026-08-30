package com.optiplant.inventory.transfers.application.port.out;

import com.optiplant.inventory.transfers.domain.model.BranchReference;
import com.optiplant.inventory.transfers.domain.model.ProductReference;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port resolving branch and product references from {@code iam}/{@code catalog}
 * (design §5.2, §6.1) — named for the need, not for those modules, since {@code transfers}
 * cannot import them. No {@code @Entity} spans a boundary; the implementation resolves
 * {@code external_id -> id} through native queries, exactly as
 * {@code inventory}'s {@code ForeignKeyResolverSpringDataRepository} does.
 */
public interface TransferReferencePort {

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.BranchNotFoundException {@code branchExternalId}
	 *     names no branch, or an inactive one
	 */
	void requireActiveBranch(UUID branchExternalId);

	/** Active products only (R-03). */
	Optional<ProductReference> findProduct(UUID productExternalId);

	/** Batch resolution — avoids one query per row (RNF-PER-01) when enriching a page of items. */
	Map<UUID, ProductReference> findProducts(Collection<UUID> productExternalIds);

	/** Batch resolution — avoids one query per row (RNF-PER-01) when enriching a page of transfers. */
	Map<UUID, BranchReference> findBranches(Collection<UUID> branchExternalIds);
}
