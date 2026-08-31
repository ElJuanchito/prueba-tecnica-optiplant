package com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.logistics.application.port.out.LogisticsRouteRepositoryPort;
import com.optiplant.inventory.logistics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.logistics.domain.exception.RouteNotFoundException;
import com.optiplant.inventory.logistics.domain.model.BranchReference;
import com.optiplant.inventory.logistics.domain.model.LogisticsRoute;
import com.optiplant.inventory.logistics.domain.model.RoutePage;
import com.optiplant.inventory.logistics.domain.model.RouteSummary;
import com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence.LogisticsReferenceSpringDataRepository.BranchDescriptorRow;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * The single {@link LogisticsRouteRepositoryPort} implementation (design §5.2, §6.1). {@link
 * #create} and {@link #save} return the branch-name-enriched {@link RouteSummary}, resolved by
 * this adapter's own batch join (PA-05) — {@code logistics} declares no reference port.
 */
@Component
public class LogisticsRoutePersistenceAdapter implements LogisticsRouteRepositoryPort {

	private final LogisticsRouteSpringDataRepository routeRepository;
	private final LogisticsReferenceSpringDataRepository referenceRepository;
	private final LogisticsRouteMapper mapper;

	public LogisticsRoutePersistenceAdapter(LogisticsRouteSpringDataRepository routeRepository,
			LogisticsReferenceSpringDataRepository referenceRepository, LogisticsRouteMapper mapper) {
		this.routeRepository = routeRepository;
		this.referenceRepository = referenceRepository;
		this.mapper = mapper;
	}

	@Override
	public RouteSummary create(NewRoute newRoute) {
		Long originBranchId = requireActiveBranchId(newRoute.originBranchExternalId());
		Long destinationBranchId = requireActiveBranchId(newRoute.destinationBranchExternalId());
		LogisticsRouteJpaEntity entity = mapper.toNewEntity(originBranchId, destinationBranchId,
				newRoute.estimatedDurationHours(), newRoute.transportCost(), newRoute.priorityLevel());
		LogisticsRouteJpaEntity saved = routeRepository.save(entity);
		return toSummary(saved);
	}

	@Override
	public Optional<LogisticsRoute> findByExternalId(UUID externalId) {
		return routeRepository.findByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public Optional<LogisticsRoute> findActiveByPair(UUID originBranchExternalId, UUID destinationBranchExternalId) {
		Long originBranchId = resolveBranchIdOrSentinel(originBranchExternalId);
		Long destinationBranchId = resolveBranchIdOrSentinel(destinationBranchExternalId);
		return routeRepository.findByOriginBranchIdAndDestinationBranchIdAndActiveTrue(originBranchId,
				destinationBranchId).map(entity -> mapper.toDomain(entity, originBranchExternalId, destinationBranchExternalId));
	}

	@Override
	public boolean existsForPair(UUID originBranchExternalId, UUID destinationBranchExternalId) {
		Long originBranchId = resolveBranchIdOrSentinel(originBranchExternalId);
		Long destinationBranchId = resolveBranchIdOrSentinel(destinationBranchExternalId);
		return routeRepository.existsByOriginBranchIdAndDestinationBranchId(originBranchId, destinationBranchId);
	}

	@Override
	public RouteSummary save(LogisticsRoute route) {
		LogisticsRouteJpaEntity entity = routeRepository.findByExternalId(route.externalId())
				.orElseThrow(() -> new RouteNotFoundException(route.externalId()));
		mapper.applyState(entity, route);
		LogisticsRouteJpaEntity saved = routeRepository.save(entity);
		return toSummary(saved);
	}

	@Override
	public RoutePage list(RouteFilter filter) {
		Page<LogisticsRouteJpaEntity> page = switch (filter.sort()) {
			case COST_ASC -> routeRepository.search(filter.activeOnly(),
					PageRequest.of(filter.page(), filter.size(), Sort.by(Sort.Direction.ASC, "transportCost")));
			case DURATION_ASC -> routeRepository.search(filter.activeOnly(),
					PageRequest.of(filter.page(), filter.size(), Sort.by(Sort.Direction.ASC, "estimatedDurationHours")));
			case PRIORITY_DESC -> routeRepository.searchOrderByPriorityRank(filter.activeOnly(),
					PageRequest.of(filter.page(), filter.size()));
		};

		Set<Long> branchIds = new HashSet<>();
		for (LogisticsRouteJpaEntity entity : page.getContent()) {
			branchIds.add(entity.getOriginBranchId());
			branchIds.add(entity.getDestinationBranchId());
		}
		Map<Long, BranchDescriptorRow> descriptors = resolveDescriptors(branchIds);

		List<RouteSummary> content = page.getContent().stream()
				.map(entity -> mapper.toSummary(entity, toBranchReference(descriptors.get(entity.getOriginBranchId())),
						toBranchReference(descriptors.get(entity.getDestinationBranchId()))))
				.toList();
		return new RoutePage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	private LogisticsRoute toDomain(LogisticsRouteJpaEntity entity) {
		UUID originBranchExternalId = resolveBranchExternalId(entity.getOriginBranchId());
		UUID destinationBranchExternalId = resolveBranchExternalId(entity.getDestinationBranchId());
		return mapper.toDomain(entity, originBranchExternalId, destinationBranchExternalId);
	}

	private RouteSummary toSummary(LogisticsRouteJpaEntity entity) {
		BranchDescriptorRow origin = referenceRepository.findBranchDescriptor(entity.getOriginBranchId()).orElse(null);
		BranchDescriptorRow destination = referenceRepository.findBranchDescriptor(entity.getDestinationBranchId())
				.orElse(null);
		return mapper.toSummary(entity, toBranchReference(origin), toBranchReference(destination));
	}

	private Map<Long, BranchDescriptorRow> resolveDescriptors(Set<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, BranchDescriptorRow> result = new HashMap<>();
		for (BranchDescriptorRow row : referenceRepository.findBranchDescriptors(ids)) {
			result.put(row.getId(), row);
		}
		return result;
	}

	private static BranchReference toBranchReference(BranchDescriptorRow row) {
		return row == null ? null : new BranchReference(row.getExternalId(), row.getName());
	}

	private UUID resolveBranchExternalId(Long branchId) {
		return referenceRepository.findBranchDescriptor(branchId).map(BranchDescriptorRow::getExternalId).orElse(null);
	}

	private Long resolveBranchIdOrSentinel(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId).orElse(-1L);
	}

	private Long requireActiveBranchId(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId)
				.orElseThrow(() -> new BranchNotFoundException(externalId));
	}
}
