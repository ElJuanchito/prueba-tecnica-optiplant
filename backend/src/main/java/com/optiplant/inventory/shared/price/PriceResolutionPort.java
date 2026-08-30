package com.optiplant.inventory.shared.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous price-resolution port (design §2, P-05, D-1).
 *
 * <p>The package is {@code shared.price}, deliberately not {@code shared.pricing}: a
 * {@code shared} subpackage sharing a module's name would invite the reader to think that module
 * leaked into {@code shared}.
 *
 * <p>The two lookups ({@link #findActiveListByExternalId} and {@link #findActiveDefaultListForBranch})
 * are separate because the contract demands two distinct error codes: a named list absent or inactive
 * is {@code price_list_not_found} (404); no list named and no active branch default is
 * {@code price_list_not_resolvable} (409) — one method returning an Optional could not distinguish
 * the two failure modes.
 *
 * <p>{@link #resolveUnitPrices} resolves prices in a single batch call for the basket (RNF-PER-02).
 * An unpriced product is absent from the returned map, never zero (R-11) — the port never invents
 * a price and never falls back to another list (P-05).
 */
public interface PriceResolutionPort {

	/**
	 * Looks up an active price list by its external ID (R-10 named list).
	 *
	 * @return the active list with cap, or {@link Optional#empty()} if not found or inactive
	 */
	Optional<AppliedPriceList> findActiveListByExternalId(UUID priceListExternalId);

	/**
	 * Looks up the active default price list configured for the given branch (R-10 fallback).
	 *
	 * @return the active default list with cap, or {@link Optional#empty()} if no active default list is configured
	 */
	Optional<AppliedPriceList> findActiveDefaultListForBranch(UUID branchExternalId);

	/**
	 * Returns price list descriptions in any state (active or inactive) for receipts (R-23),
	 * batched to prevent N+1 queries (RNF-PER-01).
	 *
	 * @param priceListExternalIds list of price list external IDs to look up
	 * @return map from price list external ID to {@link AppliedPriceList}
	 */
	Map<UUID, AppliedPriceList> describeLists(Collection<UUID> priceListExternalIds);

	/**
	 * Resolves unit prices for a collection of products under the given price list and branch on the
	 * specified operation date (R-11, RN-16).
	 *
	 * <p>Branch-specific prices beat corporate prices. Products with no eligible price row valid on
	 * {@code operationDate} are absent from the returned map (never mapped to zero).
	 *
	 * @return map from product external ID to resolved unit price
	 */
	Map<UUID, BigDecimal> resolveUnitPrices(UUID priceListExternalId, UUID branchExternalId,
			Collection<UUID> productExternalIds, LocalDate operationDate);
}
