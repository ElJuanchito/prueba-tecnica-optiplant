package com.optiplant.inventory.logistics.application.service;

import com.optiplant.inventory.logistics.application.port.in.ManageRoutesUseCase;
import com.optiplant.inventory.logistics.application.port.out.LogisticsRouteRepositoryPort;
import com.optiplant.inventory.logistics.application.port.out.LogisticsRouteRepositoryPort.NewRoute;
import com.optiplant.inventory.logistics.application.port.out.LogisticsRouteRepositoryPort.RouteFilter;
import com.optiplant.inventory.logistics.domain.exception.RouteAlreadyExistsException;
import com.optiplant.inventory.logistics.domain.exception.RouteNotFoundException;
import com.optiplant.inventory.logistics.domain.exception.SameBranchRouteException;
import com.optiplant.inventory.logistics.domain.model.LogisticsRoute;
import com.optiplant.inventory.logistics.domain.model.RouteDuration;
import com.optiplant.inventory.logistics.domain.model.RoutePage;
import com.optiplant.inventory.logistics.domain.model.RouteSummary;
import com.optiplant.inventory.logistics.domain.model.TransportCost;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Route CRUD (CU-LOG-01): equal branches or an existing ordered pair are refused before any
 * write (R-23). Deactivation is logical, never a delete (R-24, F-6).
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc ({@code transfers} module).
 */
public class ManageRoutesService implements ManageRoutesUseCase {

	private final LogisticsRouteRepositoryPort routeRepository;

	public ManageRoutesService(LogisticsRouteRepositoryPort routeRepository) {
		this.routeRepository = routeRepository;
	}

	@Override
	@Transactional
	public RouteSummary create(AuthenticatedPrincipal actor, CreateRouteCommand command) {
		if (command.originBranchExternalId().equals(command.destinationBranchExternalId())) {
			throw new SameBranchRouteException();
		}
		if (routeRepository.existsForPair(command.originBranchExternalId(), command.destinationBranchExternalId())) {
			throw new RouteAlreadyExistsException();
		}
		return routeRepository.create(new NewRoute(command.originBranchExternalId(),
				command.destinationBranchExternalId(), new RouteDuration(command.estimatedDurationHours()),
				new TransportCost(command.transportCost()), command.priorityLevel()));
	}

	@Override
	@Transactional
	public RouteSummary update(AuthenticatedPrincipal actor, UUID routeExternalId, UpdateRouteCommand command) {
		LogisticsRoute route = findOrThrow(routeExternalId);
		LogisticsRoute updated = route.update(new RouteDuration(command.estimatedDurationHours()),
				new TransportCost(command.transportCost()), command.priorityLevel());
		return routeRepository.save(updated);
	}

	@Override
	@Transactional
	public RouteSummary deactivate(AuthenticatedPrincipal actor, UUID routeExternalId) {
		LogisticsRoute route = findOrThrow(routeExternalId);
		return routeRepository.save(route.deactivate());
	}

	@Override
	@Transactional(readOnly = true)
	public RoutePage list(AuthenticatedPrincipal actor, RouteListQuery query) {
		return routeRepository.list(new RouteFilter(query.active(), query.page(), query.size()));
	}

	private LogisticsRoute findOrThrow(UUID routeExternalId) {
		return routeRepository.findByExternalId(routeExternalId)
				.orElseThrow(() -> new RouteNotFoundException(routeExternalId));
	}
}
