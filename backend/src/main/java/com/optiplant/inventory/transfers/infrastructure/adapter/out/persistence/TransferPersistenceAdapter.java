package com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDirection;
import com.optiplant.inventory.transfers.domain.model.TransferPage;
import com.optiplant.inventory.transfers.domain.model.TransferSummary;
import com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence.TransferReferenceSpringDataRepository.IdExternalIdRow;
import java.time.Instant;
import java.time.Year;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The single {@link TransferRepositoryPort} implementation (design §6.1, §6.2, §7). Every
 * returned value is an {@code external_id} UUID or a domain record — the internal numeric
 * {@code id} never leaves this package.
 *
 * <p>{@link #create} allocates {@code TRF-<yyyy>-<nnnn>} under a year-scoped PostgreSQL advisory
 * lock (D-3, §6.2, DT-11) as its first statement, then computes the next sequence number and
 * inserts — no schema sequence exists, and §2.5 forbids adding one.
 */
@Component
public class TransferPersistenceAdapter implements TransferRepositoryPort {

	private final TransferSpringDataRepository transferRepository;
	private final TransferReferenceSpringDataRepository referenceRepository;
	private final TransferMapper mapper;

	public TransferPersistenceAdapter(TransferSpringDataRepository transferRepository,
			TransferReferenceSpringDataRepository referenceRepository, TransferMapper mapper) {
		this.transferRepository = transferRepository;
		this.referenceRepository = referenceRepository;
		this.mapper = mapper;
	}

	@Override
	public Transfer create(NewTransfer newTransfer) {
		Long originBranchId = requireBranchId(newTransfer.originBranchExternalId());
		Long destinationBranchId = requireBranchId(newTransfer.destinationBranchExternalId());
		Long requestedByUserId = requireUserId(newTransfer.requestedByUserExternalId());

		Map<UUID, Long> productIdsByExternalId = new HashMap<>();
		for (NewTransferItem item : newTransfer.items()) {
			productIdsByExternalId.computeIfAbsent(item.productExternalId(), this::requireProductId);
		}

		int year = Year.now().getValue();
		// §6.2, D-3: the advisory lock MUST be the first statement — it serializes concurrent
		// creations within the same year without a schema change.
		transferRepository.allocateAdvisoryLock("transfer_number:" + year);
		int sequence = transferRepository.nextSequenceNumber("TRF-" + year + "-%");
		String transferNumber = "TRF-%d-%04d".formatted(year, sequence);

		Instant now = Instant.now();
		TransferJpaEntity entity = mapper.toNewEntity(newTransfer, transferNumber, originBranchId, destinationBranchId,
				requestedByUserId, productIdsByExternalId, now);
		TransferJpaEntity saved = transferRepository.save(entity);

		Map<Long, UUID> productExternalIdsByProductId = new HashMap<>();
		productIdsByExternalId.forEach((externalId, id) -> productExternalIdsByProductId.put(id, externalId));

		return mapper.toDomain(saved, newTransfer.originBranchExternalId(), newTransfer.destinationBranchExternalId(),
				newTransfer.requestedByUserExternalId(), null, null, productExternalIdsByProductId);
	}

	@Override
	public Optional<Transfer> lockForUpdate(UUID externalId) {
		return transferRepository.findByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public Optional<Transfer> findByExternalId(UUID externalId) {
		return transferRepository.findDetailByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public Transfer save(Transfer transfer) {
		TransferJpaEntity entity = transferRepository.findDetailByExternalId(transfer.externalId())
				.orElseThrow(() -> new IllegalStateException("No transfer found for external id " + transfer.externalId()));
		Long dispatchedByUserId = transfer.dispatchedByUserExternalId() == null ? null
				: requireUserId(transfer.dispatchedByUserExternalId());
		Long receivedByUserId = transfer.receivedByUserExternalId() == null ? null
				: requireUserId(transfer.receivedByUserExternalId());

		mapper.applyState(entity, transfer, dispatchedByUserId, receivedByUserId);
		transferRepository.save(entity);
		return transfer;
	}

	@Override
	public TransferPage list(TransferFilter filter) {
		Long originId = null;
		Long destinationId = null;
		Long eitherId = null;
		if (filter.callerBranchExternalId() != null) {
			Long callerId = resolveBranchIdOrSentinel(filter.callerBranchExternalId());
			if (filter.direction() == TransferDirection.OUTBOUND) {
				originId = callerId;
			} else if (filter.direction() == TransferDirection.INBOUND) {
				destinationId = callerId;
			} else {
				eitherId = callerId;
			}
		}
		String status = filter.status() == null ? null : filter.status().name();
		PageRequest pageRequest = PageRequest.of(filter.page(), filter.size());

		Page<TransferJpaEntity> page = "priority".equals(filter.sort())
				? transferRepository.searchOrderByPriority(originId, destinationId, eitherId, status, filter.from(),
						filter.to(), pageRequest)
				: transferRepository.searchOrderByCreatedAt(originId, destinationId, eitherId, status, filter.from(),
						filter.to(), pageRequest);

		Set<Long> branchIds = new HashSet<>();
		for (TransferJpaEntity entity : page.getContent()) {
			branchIds.add(entity.getOriginBranchId());
			branchIds.add(entity.getDestinationBranchId());
		}
		Map<Long, UUID> branchExternalIds = resolveExternalIds(branchIds, referenceRepository::findBranchExternalIds);

		List<TransferSummary> content = page.getContent().stream()
				.map(entity -> mapper.toSummary(entity, branchExternalIds.get(entity.getOriginBranchId()),
						branchExternalIds.get(entity.getDestinationBranchId())))
				.toList();
		return new TransferPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	private Transfer toDomain(TransferJpaEntity entity) {
		Set<Long> branchIds = Set.of(entity.getOriginBranchId(), entity.getDestinationBranchId());
		Map<Long, UUID> branchExternalIds = resolveExternalIds(branchIds, referenceRepository::findBranchExternalIds);

		Set<Long> userIds = new HashSet<>();
		userIds.add(entity.getRequestedByUserId());
		if (entity.getDispatchedByUserId() != null) {
			userIds.add(entity.getDispatchedByUserId());
		}
		if (entity.getReceivedByUserId() != null) {
			userIds.add(entity.getReceivedByUserId());
		}
		Map<Long, UUID> userExternalIds = resolveExternalIds(userIds, referenceRepository::findUserExternalIds);

		Set<Long> productIds = entity.getItems().stream().map(TransferItemJpaEntity::getProductId)
				.collect(Collectors.toSet());
		Map<Long, UUID> productExternalIds = resolveExternalIds(productIds, referenceRepository::findProductExternalIds);

		UUID dispatchedByUserExternalId = entity.getDispatchedByUserId() == null ? null
				: userExternalIds.get(entity.getDispatchedByUserId());
		UUID receivedByUserExternalId = entity.getReceivedByUserId() == null ? null
				: userExternalIds.get(entity.getReceivedByUserId());

		return mapper.toDomain(entity, branchExternalIds.get(entity.getOriginBranchId()),
				branchExternalIds.get(entity.getDestinationBranchId()), userExternalIds.get(entity.getRequestedByUserId()),
				dispatchedByUserExternalId, receivedByUserExternalId, productExternalIds);
	}

	private Map<Long, UUID> resolveExternalIds(Collection<Long> ids,
			Function<List<Long>, List<IdExternalIdRow>> lookup) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, UUID> result = new HashMap<>();
		for (IdExternalIdRow row : lookup.apply(List.copyOf(ids))) {
			result.put(row.getId(), row.getExternalId());
		}
		return result;
	}

	private Long resolveBranchIdOrSentinel(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId).orElse(-1L);
	}

	private Long requireBranchId(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No active branch found for external id " + externalId));
	}

	private Long requireUserId(UUID externalId) {
		return referenceRepository.findUserIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
	}

	private Long requireProductId(UUID externalId) {
		return referenceRepository.findActiveProductIdByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));
	}
}
