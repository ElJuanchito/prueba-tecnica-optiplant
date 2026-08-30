package com.optiplant.inventory.shared.route;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous lead-time lookup for an ordered branch pair (contract P-11, design §2.1).
 * {@code transfers} calls this at dispatch to precompute {@code estimated_arrival_at}
 * (HU-LOG-03) without importing {@code logistics} — the module graph gains only
 * {@code transfers -> shared <- logistics}, no direct edge between the two.
 *
 * <p>The package is {@code shared.route}, deliberately not {@code shared.logistics}: a
 * {@code shared} subpackage sharing a module's name would invite the reader to think that module
 * leaked into {@code shared}.
 */
public interface RouteLeadTimePort {

	/**
	 * @return the route's estimated duration, or {@link Optional#empty()} when no route exists
	 *     for the ordered pair or the route is inactive (R-24). A missing or inactive route MUST
	 *     NOT fail the dispatch — the operator's own ETA then stands (P-11).
	 */
	Optional<Duration> estimatedLeadTime(UUID originBranchExternalId, UUID destinationBranchExternalId);
}
