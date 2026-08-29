package com.optiplant.inventory.logistics.infrastructure.adapter.out.route;

import com.optiplant.inventory.logistics.application.port.out.LogisticsRouteRepositoryPort;
import com.optiplant.inventory.logistics.domain.model.LogisticsRoute;
import com.optiplant.inventory.shared.route.RouteLeadTimePort;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single {@link RouteLeadTimePort} implementation (P-11, design §2.1, §6.5). Empty when no
 * active route exists for the ordered pair (R-24) — a missing or inactive route MUST NOT fail
 * {@code transfers}' dispatch; the operator's own ETA then stands.
 */
@Component
public class RouteLeadTimeAdapter implements RouteLeadTimePort {

	private final LogisticsRouteRepositoryPort routeRepository;

	public RouteLeadTimeAdapter(LogisticsRouteRepositoryPort routeRepository) {
		this.routeRepository = routeRepository;
	}

	@Override
	public Optional<Duration> estimatedLeadTime(UUID originBranchExternalId, UUID destinationBranchExternalId) {
		return routeRepository.findActiveByPair(originBranchExternalId, destinationBranchExternalId)
				.map(LogisticsRoute::leadTime);
	}
}
