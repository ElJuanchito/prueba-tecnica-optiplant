package com.optiplant.inventory.shared.availability;

import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous network-wide availability read port (contract P-03, design §2).
 * Answers CU-EXT-01 without duplicating the stock query semantics defined in
 * {@code inventory}.
 *
 * <p>The package is {@code shared.availability}, deliberately not {@code shared.inventory}
 * (the {@code shared.route} precedent).
 */
public interface NetworkAvailabilityPort {

	/**
	 * @return the product's network-wide availability across all active branches,
	 *     or {@link Optional#empty()} when {@code productExternalId} names no product (R-24, D-2).
	 */
	Optional<NetworkAvailabilityView> networkAvailability(UUID productExternalId);
}
