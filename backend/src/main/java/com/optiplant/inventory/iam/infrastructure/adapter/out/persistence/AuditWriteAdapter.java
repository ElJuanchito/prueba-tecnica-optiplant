package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.application.port.out.AuditQueryPort;
import com.optiplant.inventory.iam.domain.model.AuditRecord;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implements both the cross-module {@link AuditWritePort} (write, synchronous —
 * CLAUDE.md's atomic-effects invariant; deliberately carries no {@code @Transactional}
 * of its own, so a call always joins the caller's already-open transaction instead of
 * starting a new one) and the {@code iam}-local {@link AuditQueryPort} (read). One
 * physical table backs both concerns; task 4.4 assigns the query methods to "the
 * persistence adapter" (singular), the same class task 4.3 names for the write side —
 * mirroring how {@code RefreshTokenPersistenceAdapter} grew read methods onto an
 * originally write-only shape across slices 2a/2b.
 */
@Component
public class AuditWriteAdapter implements AuditWritePort, AuditQueryPort {

	private final AuditLogSpringDataRepository auditLogRepository;
	private final UserSpringDataRepository userRepository;

	public AuditWriteAdapter(AuditLogSpringDataRepository auditLogRepository, UserSpringDataRepository userRepository) {
		this.auditLogRepository = auditLogRepository;
		this.userRepository = userRepository;
	}

	@Override
	public void record(AuditEntryCommand command) {
		AuditLogJpaEntity entity = new AuditLogJpaEntity();
		entity.setUserId(requireUserId(command.actorUserId()));
		entity.setBranchId(command.branchId() != null ? requireBranchId(command.branchId()) : null);
		entity.setAction(command.action());
		entity.setEntityName(command.entityName());
		entity.setEntityId(command.entityId());
		entity.setPayloadBefore(command.payloadBefore());
		entity.setPayloadAfter(command.payloadAfter());
		entity.setIpAddress(command.ipAddress());
		entity.setCreatedAt(Instant.now());
		auditLogRepository.save(entity);
	}

	@Override
	public AuditPage query(AuditFilter filter) {
		Long userId = filter.actorUserExternalId() != null ? resolveUserIdForFilter(filter.actorUserExternalId())
				: null;
		Long branchId = filter.branchExternalId() != null ? resolveBranchIdForFilter(filter.branchExternalId())
				: null;

		// Ordering (most recent first) is fixed inside the native query itself, not
		// via Pageable's Sort — Spring Data JPA rejects dynamic sorting on a native
		// query (found by executing, not by reading — CLAUDE.md).
		Page<AuditLogJpaEntity> page = auditLogRepository.search(userId, branchId, filter.entityName(),
				filter.action(), filter.from(), filter.to(), PageRequest.of(filter.page(), filter.size()));

		List<AuditRecord> content = page.getContent().stream().map(this::toDomain).toList();
		return new AuditPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	// Write path: an actor/branch external id that fails to resolve is a genuine
	// data-integrity problem, not a normal outcome — fail loudly (mirrors
	// RefreshTokenPersistenceAdapter.persist's IllegalStateException for the same
	// situation) rather than silently writing a wrong or sentinel foreign key.
	private Long requireUserId(UUID externalId) {
		return userRepository.findIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
	}

	private Long requireBranchId(UUID externalId) {
		return userRepository.findBranchIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No branch found for external id " + externalId));
	}

	// Query path: a filter value that matches nothing must yield an empty page, not
	// an error — a client-submitted UUID is not guaranteed to name an existing row.
	// -1 is a safe sentinel: GENERATED ALWAYS AS IDENTITY never assigns it.
	private Long resolveUserIdForFilter(UUID externalId) {
		return userRepository.findIdByExternalId(externalId).orElse(-1L);
	}

	private Long resolveBranchIdForFilter(UUID externalId) {
		return userRepository.findBranchIdByExternalId(externalId).orElse(-1L);
	}

	private AuditRecord toDomain(AuditLogJpaEntity entity) {
		UUID actorExternalId = entity.getUserId() != null
				? userRepository.findExternalIdById(entity.getUserId()).orElse(null)
				: null;
		UUID branchExternalId = entity.getBranchId() != null
				? userRepository.findBranchExternalId(entity.getBranchId()).orElse(null)
				: null;
		return new AuditRecord(entity.getExternalId(), actorExternalId, branchExternalId, entity.getAction(),
				entity.getEntityName(), entity.getEntityId(), entity.getPayloadBefore(), entity.getPayloadAfter(),
				entity.getIpAddress(), entity.getCreatedAt());
	}
}
