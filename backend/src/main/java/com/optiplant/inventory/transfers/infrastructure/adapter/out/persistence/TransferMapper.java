package com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort.NewTransfer;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort.NewTransferItem;
import com.optiplant.inventory.transfers.domain.model.BranchReference;
import com.optiplant.inventory.transfers.domain.model.CarrierName;
import com.optiplant.inventory.transfers.domain.model.SettledQuantity;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferNumber;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.transfers.domain.model.TransferSummary;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Entity &harr; domain mapping for {@code transfers} / {@code transfer_items} (design §6.1). The
 * sole reader and writer of the F-1 priority token ({@link TransferNotes#render()} /
 * {@link TransferNotes#parse(String)}) — no other class in this module touches {@code notes} —
 * and the only place {@code updated_at} is set, since the schema has no trigger for it (F-5, §8).
 *
 * <p>Hand-written rather than MapStruct: every {@code branch_id}/{@code product_id}/{@code
 * user_id} on the entity is a plain {@code Long} the caller must already have resolved to/from an
 * {@code external_id} (design §6.1), and the F-1 token round trip is logic MapStruct's
 * declarative field mapping cannot express.
 */
@Component
public class TransferMapper {

	/** Builds the domain aggregate straight from a stored entity; ids are supplied already resolved. */
	Transfer toDomain(TransferJpaEntity entity, UUID originBranchExternalId, UUID destinationBranchExternalId,
			UUID requestedByUserExternalId, UUID dispatchedByUserExternalId, UUID receivedByUserExternalId,
			Map<Long, UUID> productExternalIdsByProductId) {
		return new Transfer(entity.getExternalId(), new TransferNumber(entity.getTransferNumber()),
				TransferStatus.valueOf(entity.getStatus()), originBranchExternalId, destinationBranchExternalId,
				requestedByUserExternalId, dispatchedByUserExternalId, receivedByUserExternalId,
				toCarrierName(entity.getCarrierName()), entity.getTrackingNumber(), entity.getDispatchedAt(),
				entity.getEstimatedArrivalAt(), entity.getActualArrivalAt(), TransferNotes.parse(entity.getNotes()),
				entity.getCreatedAt(), entity.getUpdatedAt(),
				entity.getItems().stream().map(item -> toDomainItem(item, productExternalIdsByProductId)).toList());
	}

	/** The listing projection — {@code branchExternalId}s only, enriched with names by the application layer. */
	TransferSummary toSummary(TransferJpaEntity entity, UUID originBranchExternalId, UUID destinationBranchExternalId) {
		return new TransferSummary(entity.getExternalId(), new TransferNumber(entity.getTransferNumber()),
				TransferStatus.valueOf(entity.getStatus()), TransferNotes.parse(entity.getNotes()).priority(),
				new BranchReference(originBranchExternalId, null), new BranchReference(destinationBranchExternalId, null),
				entity.getCreatedAt(), entity.getEstimatedArrivalAt());
	}

	/** Builds a brand-new {@code REQUESTED} entity (create path, §6.2) — ids and number already allocated. */
	TransferJpaEntity toNewEntity(NewTransfer newTransfer, String transferNumber, Long originBranchId,
			Long destinationBranchId, Long requestedByUserId, Map<UUID, Long> productIdsByExternalId, Instant now) {
		TransferJpaEntity entity = new TransferJpaEntity();
		entity.setTransferNumber(transferNumber);
		entity.setOriginBranchId(originBranchId);
		entity.setDestinationBranchId(destinationBranchId);
		entity.setRequestedByUserId(requestedByUserId);
		entity.setStatus(TransferStatus.REQUESTED.name());
		entity.setNotes(newTransfer.notes().render());
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		for (NewTransferItem item : newTransfer.items()) {
			TransferItemJpaEntity itemEntity = new TransferItemJpaEntity();
			itemEntity.setProductId(productIdsByExternalId.get(item.productExternalId()));
			itemEntity.setRequestedQuantity(item.requestedQuantity().value());
			itemEntity.setDispatchedQuantity(SettledQuantity.zero().value());
			itemEntity.setReceivedQuantity(SettledQuantity.zero().value());
			itemEntity.setDiscrepancyQuantity(SettledQuantity.zero().value());
			entity.addItem(itemEntity);
		}
		return entity;
	}

	/**
	 * Applies every transition-mutable field from {@code transfer} back onto its already-loaded
	 * {@code entity} (update path — approve/reject/dispatch/receive/cancel). {@code
	 * transferNumber}, the branch ids and {@code requestedByUserId} never change after creation
	 * and are left untouched. Sets {@code updated_at} explicitly (F-5, §8: no trigger exists).
	 */
	void applyState(TransferJpaEntity entity, Transfer transfer, Long dispatchedByUserId, Long receivedByUserId) {
		entity.setStatus(transfer.status().name());
		entity.setCarrierName(transfer.carrierName() == null ? null : transfer.carrierName().value());
		entity.setTrackingNumber(transfer.trackingNumber());
		entity.setDispatchedAt(transfer.dispatchedAt());
		entity.setEstimatedArrivalAt(transfer.estimatedArrivalAt());
		entity.setActualArrivalAt(transfer.actualArrivalAt());
		entity.setDispatchedByUserId(dispatchedByUserId);
		entity.setReceivedByUserId(receivedByUserId);
		entity.setNotes(transfer.notes().render());
		entity.setUpdatedAt(transfer.updatedAt());

		Map<UUID, TransferItemJpaEntity> byExternalId = entity.getItems().stream()
				.collect(Collectors.toMap(TransferItemJpaEntity::getExternalId, Function.identity()));
		for (TransferItem item : transfer.items()) {
			TransferItemJpaEntity itemEntity = byExternalId.get(item.externalId());
			if (itemEntity == null) {
				continue;
			}
			itemEntity.setRequestedQuantity(item.requestedQuantity().value());
			itemEntity.setDispatchedQuantity(item.dispatchedQuantity().value());
			itemEntity.setReceivedQuantity(item.receivedQuantity().value());
			itemEntity.setDiscrepancyQuantity(item.discrepancyQuantity().value());
			itemEntity.setDiscrepancyReason(item.discrepancyReason());
		}
	}

	private TransferItem toDomainItem(TransferItemJpaEntity entity, Map<Long, UUID> productExternalIdsByProductId) {
		UUID productExternalId = productExternalIdsByProductId.get(entity.getProductId());
		return new TransferItem(entity.getExternalId(), productExternalId,
				new TransferQuantity(entity.getRequestedQuantity()), new SettledQuantity(entity.getDispatchedQuantity()),
				new SettledQuantity(entity.getReceivedQuantity()), new SettledQuantity(entity.getDiscrepancyQuantity()),
				entity.getDiscrepancyReason());
	}

	private static CarrierName toCarrierName(String value) {
		return value == null || value.isBlank() ? null : new CarrierName(value);
	}
}
