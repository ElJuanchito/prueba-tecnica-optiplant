package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort;
import com.optiplant.inventory.notifications.domain.exception.AlertNotFoundException;
import com.optiplant.inventory.notifications.domain.model.Alert;
import com.optiplant.inventory.notifications.domain.model.AlertDedupKey;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The single {@link AlertRepositoryPort} implementation (design §5.2, §6.3, DT-09). Every
 * returned value is an {@code external_id} UUID or a domain record — the internal numeric
 * {@code id} never leaves this package.
 */
@Component
public class AlertPersistenceAdapter implements AlertRepositoryPort {

	private final AlertSpringDataRepository alertRepository;
	private final AlertForeignKeyResolverSpringDataRepository foreignKeyResolver;
	private final AlertMapper mapper;

	public AlertPersistenceAdapter(AlertSpringDataRepository alertRepository,
			AlertForeignKeyResolverSpringDataRepository foreignKeyResolver, AlertMapper mapper) {
		this.alertRepository = alertRepository;
		this.foreignKeyResolver = foreignKeyResolver;
		this.mapper = mapper;
	}

	@Override
	public Optional<Alert> findUnresolvedByDedupKey(AlertDedupKey key) {
		Long branchId = requireBranchId(key.branchExternalId());
		return alertRepository.findUnresolvedByDedupKey(branchId, key.alertType(), key.title()).map(this::toDomain);
	}

	@Override
	public Alert create(NewAlert newAlert) {
		Long branchId = requireBranchId(newAlert.branchExternalId());
		SystemAlertJpaEntity entity = new SystemAlertJpaEntity();
		entity.setBranchId(branchId);
		entity.setAlertType(newAlert.alertType());
		entity.setSeverity(newAlert.severity());
		entity.setTitle(newAlert.title());
		entity.setMessage(newAlert.message());
		entity.setResolved(false);
		entity.setCreatedAt(Instant.now());
		return toDomain(alertRepository.save(entity));
	}

	@Override
	public Alert markResolved(UUID externalId, UUID actorUserExternalId, Instant resolvedAt) {
		SystemAlertJpaEntity entity = alertRepository.findByExternalId(externalId)
				.orElseThrow(() -> new AlertNotFoundException(externalId));
		entity.setResolved(true);
		entity.setResolvedAt(resolvedAt);
		entity.setResolvedByUserId(requireUserId(actorUserExternalId));
		return toDomain(alertRepository.save(entity));
	}

	@Override
	public Optional<Alert> findByExternalIdVisibleTo(UUID externalId, UUID branchExternalId) {
		Optional<SystemAlertJpaEntity> found = alertRepository.findByExternalId(externalId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		if (branchExternalId != null) {
			Long ownBranchId = resolveBranchIdOrSentinel(branchExternalId);
			if (!Objects.equals(found.get().getBranchId(), ownBranchId)) {
				// R-19/R-24: another branch's alert responds as if it did not exist — 404, never 403.
				return Optional.empty();
			}
		}
		return found.map(this::toDomain);
	}

	@Override
	public AlertPage list(AlertFilter filter) {
		Long branchId = filter.branchExternalId() == null ? null
				: resolveBranchIdOrSentinel(filter.branchExternalId());
		Page<SystemAlertJpaEntity> page = alertRepository.search(branchId, filter.resolved(), filter.alertType(),
				filter.severity(), PageRequest.of(filter.page(), filter.size()));
		return new AlertPage(page.getContent().stream().map(this::toDomain).toList(), page.getTotalElements(),
				filter.page(), filter.size());
	}

	@Override
	public void lockAlertScope(AlertDedupKey key) {
		String lockKey = key.branchExternalId() + ":" + key.alertType() + ":" + key.subjectToken();
		alertRepository.advisoryLock(lockKey);
	}

	private Alert toDomain(SystemAlertJpaEntity entity) {
		UUID branchExternalId = entity.getBranchId() == null ? null
				: foreignKeyResolver.findBranchExternalId(entity.getBranchId()).orElse(null);
		UUID resolvedByUserExternalId = entity.getResolvedByUserId() == null ? null
				: foreignKeyResolver.findUserExternalId(entity.getResolvedByUserId()).orElse(null);
		return mapper.toDomain(entity, branchExternalId, resolvedByUserExternalId);
	}

	private Long requireBranchId(UUID externalId) {
		return foreignKeyResolver.findBranchIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No branch found for external id " + externalId));
	}

	private Long requireUserId(UUID externalId) {
		return foreignKeyResolver.findUserIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
	}

	private Long resolveBranchIdOrSentinel(UUID externalId) {
		return foreignKeyResolver.findBranchIdByExternalId(externalId).orElse(-1L);
	}
}
