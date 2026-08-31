package com.optiplant.inventory.inventory.application.port.in;

import com.optiplant.inventory.inventory.domain.model.NetworkAvailability;
import com.optiplant.inventory.inventory.domain.model.StockPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Read a product's stock (CU-INV-03, CU-INV-04, design §5.1). Every method takes an
 * {@link AuthenticatedPrincipal} — unlike {@code catalog}, whose reads deliberately take none —
 * because the branch dimension exists here and RN-14 forbids it arriving from the client, so a
 * read that cannot see its caller cannot be scoped.
 */
public interface QueryStockUseCase {

	/**
	 * @throws com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException
	 *     when {@code actor} is a corporate {@code ADMIN} (contract §5)
	 */
	StockPage listOwnBranchStock(AuthenticatedPrincipal actor, StockQuery query);

	/**
	 * Read-only, every active branch (RN-08); marks the caller's own branch, absent for a
	 * corporate {@code ADMIN} (R-04).
	 *
	 * @throws com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException
	 *     when {@code productExternalId} names no product
	 */
	NetworkAvailability networkAvailability(AuthenticatedPrincipal actor, UUID productExternalId);

	/**
	 * Read-only, every active branch (RN-08); actor-free overload for network-wide
	 * queries like CU-EXT-01 (design §2, D-3).
	 *
	 * @throws com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException
	 *     when {@code productExternalId} names no product
	 */
	NetworkAvailability networkAvailability(UUID productExternalId);

	record StockQuery(UUID productExternalId, boolean belowThreshold, String sort, int page, int size) {
	}
}
