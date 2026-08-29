package com.optiplant.inventory.transfers.domain.model;

import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.service.TransferApprovalPolicy;
import com.optiplant.inventory.transfers.domain.service.TransferApprovalPolicy.ApprovalOutcome;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy.DispatchApplication;
import com.optiplant.inventory.transfers.domain.service.TransferStateMachine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain representation of one {@code transfers} row plus its items (design §3.2). Immutable —
 * every mutator returns a new instance after consulting {@link TransferStateMachine} first
 * (R-01), so the five-state machine cannot be bypassed by reaching for a setter — there are
 * none. {@code items} is copied defensively and exposed unmodifiable.
 */
public record Transfer(UUID externalId, TransferNumber number, TransferStatus status, UUID originBranchExternalId,
		UUID destinationBranchExternalId, UUID requestedByUserExternalId, UUID dispatchedByUserExternalId,
		UUID receivedByUserExternalId, CarrierName carrierName, String trackingNumber, Instant dispatchedAt,
		Instant estimatedArrivalAt, Instant actualArrivalAt, TransferNotes notes, Instant createdAt,
		Instant updatedAt, List<TransferItem> items) {

	public Transfer {
		items = items == null ? List.of() : List.copyOf(items);
	}

	/** R-07 &rarr; {@code IN_PREPARATION}; overwrites {@code requestedQuantity} with the agreed value (PA-02). */
	public Transfer approve(List<ApprovedLine> lines, Instant at) {
		TransferStateMachine.require(status, TransferTransition.APPROVE);
		ApprovalOutcome outcome = TransferApprovalPolicy.apply(items, lines);
		return withNotesAndItems(TransferStatus.IN_PREPARATION, notes, outcome.observations(), outcome.items(), at);
	}

	/** R-09 &rarr; {@code CANCELLED}. */
	public Transfer reject(TransferReason reason, Instant at) {
		TransferStateMachine.require(status, TransferTransition.REJECT);
		return withNotesAndItems(TransferStatus.CANCELLED, notes, List.of("Rejected: " + reason.value()), items, at);
	}

	/** R-10/R-13 &rarr; {@code IN_TRANSIT}. */
	public Transfer dispatch(DispatchDetails details, List<DispatchLine> lines, Instant at) {
		TransferStateMachine.require(status, TransferTransition.DISPATCH);
		DispatchApplication application = TransferDispatchPolicy.apply(items, lines);
		Transfer withNotes = withNotesAndItems(TransferStatus.IN_TRANSIT, notes, application.observations(),
				application.items(), at);
		return new Transfer(withNotes.externalId(), withNotes.number(), withNotes.status(),
				withNotes.originBranchExternalId(), withNotes.destinationBranchExternalId(),
				withNotes.requestedByUserExternalId(), details.dispatchedByUserExternalId(),
				withNotes.receivedByUserExternalId(), details.carrierName(), details.trackingNumber(),
				details.dispatchedAt(), details.estimatedArrivalAt(), withNotes.actualArrivalAt(),
				withNotes.notes(), withNotes.createdAt(), withNotes.updatedAt(), withNotes.items());
	}

	/** R-17/R-18 &rarr; {@code RECEIVED} or {@code RECEIVED_WITH_DISCREPANCY}, resolved by {@code outcome.status()}. */
	public Transfer receive(ReceiptOutcome outcome, UUID receiver, Instant at) {
		TransferStateMachine.require(status, TransferTransition.RECEIVE);
		Map<UUID, ReceiptLine> byItem = new HashMap<>();
		for (ReceiptLine line : outcome.lines()) {
			byItem.put(line.itemExternalId(), line);
		}
		List<TransferItem> updatedItems = new ArrayList<>();
		for (TransferItem item : items) {
			ReceiptLine line = byItem.get(item.externalId());
			if (line == null) {
				throw new TransferItemNotFoundException(item.externalId());
			}
			updatedItems.add(new TransferItem(item.externalId(), item.productExternalId(), item.requestedQuantity(),
					item.dispatchedQuantity(), line.receivedQuantity(), line.discrepancyQuantity(),
					line.discrepancyReason()));
		}
		return new Transfer(externalId, number, outcome.status(), originBranchExternalId, destinationBranchExternalId,
				requestedByUserExternalId, dispatchedByUserExternalId, receiver, carrierName, trackingNumber,
				dispatchedAt, estimatedArrivalAt, at, notes, createdAt, at, updatedItems);
	}

	/** R-21 &rarr; {@code CANCELLED}, open to a manager of either side. */
	public Transfer cancel(TransferReason reason, Instant at) {
		TransferStateMachine.require(status, TransferTransition.CANCEL);
		return withNotesAndItems(TransferStatus.CANCELLED, notes, List.of("Cancelled: " + reason.value()), items, at);
	}

	/** {@code true} when {@code branchExternalId} is this transfer's origin or destination. */
	public boolean involves(UUID branchExternalId) {
		return originBranchExternalId.equals(branchExternalId) || destinationBranchExternalId.equals(branchExternalId);
	}

	/** R-27: {@code actual - estimated} in hours, {@link Optional#empty()} when either bound is absent. */
	public Optional<BigDecimal> deviationHours() {
		if (estimatedArrivalAt == null || actualArrivalAt == null) {
			return Optional.empty();
		}
		BigDecimal seconds = BigDecimal.valueOf(Duration.between(estimatedArrivalAt, actualArrivalAt).getSeconds());
		return Optional.of(seconds.divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP));
	}

	private Transfer withNotesAndItems(TransferStatus newStatus, TransferNotes baseNotes,
			List<String> newObservations, List<TransferItem> newItems, Instant at) {
		TransferNotes updatedNotes = baseNotes;
		for (String observation : newObservations) {
			updatedNotes = updatedNotes.withObservation(observation);
		}
		return new Transfer(externalId, number, newStatus, originBranchExternalId, destinationBranchExternalId,
				requestedByUserExternalId, dispatchedByUserExternalId, receivedByUserExternalId, carrierName,
				trackingNumber, dispatchedAt, estimatedArrivalAt, actualArrivalAt, updatedNotes, createdAt, at,
				newItems);
	}
}
