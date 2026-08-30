package com.optiplant.inventory.logistics.application.port.out;

import com.optiplant.inventory.logistics.domain.model.LogisticsRoute;
import com.optiplant.inventory.logistics.domain.model.RouteDuration;
import com.optiplant.inventory.logistics.domain.model.RoutePage;
import com.optiplant.inventory.logistics.domain.model.RoutePriority;
import com.optiplant.inventory.logistics.domain.model.RouteSummary;
import com.optiplant.inventory.logistics.domain.model.TransportCost;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for {@code logistics_routes} persistence (design §5.2). {@link #create} and
 * {@link #save} return the branch-name-enriched {@link RouteSummary}, resolved by the adapter's
 * own join — {@code logistics} declares no reference port (only three out-ports, design §5.2),
 * so name resolution for a single route happens here rather than at the application layer.
 */
public interface LogisticsRouteRepositoryPort {

	RouteSummary create(NewRoute newRoute);

	/** No lock — routes carry no version column and are edited by a single corporate {@code ADMIN} flow. */
	Optional<LogisticsRoute> findByExternalId(UUID externalId);

	/** Active only (R-24) — the read {@code RouteLeadTimeAdapter} (S2) consumes for P-11. */
	Optional<LogisticsRoute> findActiveByPair(UUID originBranchExternalId, UUID destinationBranchExternalId);

	/** Backs R-23's duplicate-pair refusal, mirroring {@code uq_route_pair}. */
	boolean existsForPair(UUID originBranchExternalId, UUID destinationBranchExternalId);

	RouteSummary save(LogisticsRoute route);

	RoutePage list(RouteFilter filter);

	record NewRoute(UUID originBranchExternalId, UUID destinationBranchExternalId,
			RouteDuration estimatedDurationHours, TransportCost transportCost, RoutePriority priorityLevel) {
	}

	record RouteFilter(Boolean activeOnly, int page, int size) {
	}
}
