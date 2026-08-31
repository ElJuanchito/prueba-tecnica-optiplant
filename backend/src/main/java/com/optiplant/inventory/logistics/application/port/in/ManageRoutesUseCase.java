package com.optiplant.inventory.logistics.application.port.in;

import com.optiplant.inventory.logistics.domain.model.RoutePage;
import com.optiplant.inventory.logistics.domain.model.RoutePriority;
import com.optiplant.inventory.logistics.domain.model.RouteSort;
import com.optiplant.inventory.logistics.domain.model.RouteSummary;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.UUID;

/** Route CRUD (CU-LOG-01, design §5.1) — corporate {@code ADMIN} only, no branch scope. */
public interface ManageRoutesUseCase {

	/**
	 * @throws com.optiplant.inventory.logistics.domain.exception.SameBranchRouteException
	 *     origin equals destination (R-23)
	 * @throws com.optiplant.inventory.logistics.domain.exception.RouteAlreadyExistsException
	 *     a route already exists for this ordered pair (R-23, {@code uq_route_pair})
	 */
	RouteSummary create(AuthenticatedPrincipal actor, CreateRouteCommand command);

	/**
	 * @throws com.optiplant.inventory.logistics.domain.exception.RouteNotFoundException unknown route
	 */
	RouteSummary update(AuthenticatedPrincipal actor, UUID routeExternalId, UpdateRouteCommand command);

	/**
	 * @throws com.optiplant.inventory.logistics.domain.exception.RouteNotFoundException unknown route
	 */
	RouteSummary deactivate(AuthenticatedPrincipal actor, UUID routeExternalId);

	RoutePage list(AuthenticatedPrincipal actor, RouteListQuery query);

	record CreateRouteCommand(UUID originBranchExternalId, UUID destinationBranchExternalId,
			BigDecimal estimatedDurationHours, BigDecimal transportCost, RoutePriority priorityLevel) {
	}

	record UpdateRouteCommand(BigDecimal estimatedDurationHours, BigDecimal transportCost,
			RoutePriority priorityLevel) {
	}

	record RouteListQuery(Boolean active, RouteSort sort, int page, int size) {
	}
}
