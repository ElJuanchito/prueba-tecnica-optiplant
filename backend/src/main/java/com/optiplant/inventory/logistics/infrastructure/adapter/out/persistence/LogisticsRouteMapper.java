package com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.logistics.domain.model.BranchReference;
import com.optiplant.inventory.logistics.domain.model.LogisticsRoute;
import com.optiplant.inventory.logistics.domain.model.RouteDuration;
import com.optiplant.inventory.logistics.domain.model.RoutePriority;
import com.optiplant.inventory.logistics.domain.model.RouteSummary;
import com.optiplant.inventory.logistics.domain.model.TransportCost;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entity &harr; domain mapping for {@code logistics_routes} (design §6.1). Hand-written — the
 * origin/destination branch names are resolved by the persistence adapter's own join, not
 * declared through a reference port (design §5.2's three out-ports, PA-05), so this mapper
 * accepts them as extra parameters exactly as {@code inventory}'s {@code BranchInventoryMapper}
 * accepts externally resolved {@code external_id}s.
 */
@Component
public class LogisticsRouteMapper {

	LogisticsRoute toDomain(LogisticsRouteJpaEntity entity, UUID originBranchExternalId,
			UUID destinationBranchExternalId) {
		return new LogisticsRoute(entity.getExternalId(), originBranchExternalId, destinationBranchExternalId,
				new RouteDuration(entity.getEstimatedDurationHours()), new TransportCost(entity.getTransportCost()),
				entity.getPriorityLevel(), entity.isActive(), entity.getCreatedAt());
	}

	RouteSummary toSummary(LogisticsRouteJpaEntity entity, BranchReference originBranch,
			BranchReference destinationBranch) {
		return new RouteSummary(entity.getExternalId(), originBranch, destinationBranch,
				entity.getEstimatedDurationHours(), entity.getTransportCost(), entity.getPriorityLevel(),
				entity.isActive(), entity.getCreatedAt());
	}

	LogisticsRouteJpaEntity toNewEntity(Long originBranchId, Long destinationBranchId, RouteDuration duration,
			TransportCost cost, RoutePriority priority) {
		LogisticsRouteJpaEntity entity = new LogisticsRouteJpaEntity();
		entity.setOriginBranchId(originBranchId);
		entity.setDestinationBranchId(destinationBranchId);
		entity.setEstimatedDurationHours(duration.value());
		entity.setTransportCost(cost.value());
		entity.setPriorityLevel(priority);
		entity.setActive(true);
		return entity;
	}

	void applyState(LogisticsRouteJpaEntity entity, LogisticsRoute route) {
		entity.setEstimatedDurationHours(route.estimatedDurationHours().value());
		entity.setTransportCost(route.transportCost().value());
		entity.setPriorityLevel(route.priorityLevel());
		entity.setActive(route.active());
	}
}
