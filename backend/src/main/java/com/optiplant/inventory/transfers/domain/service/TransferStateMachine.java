package com.optiplant.inventory.transfers.domain.service;

import com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.transfers.domain.model.TransferTransition;
import java.util.Map;
import java.util.Set;

/**
 * The only authority on R-01 (design §3.3): {@code REQUESTED -> IN_PREPARATION -> IN_TRANSIT ->
 * RECEIVED | RECEIVED_WITH_DISCREPANCY} is mandatory and MUST NOT be skipped; {@code CANCELLED}
 * is reachable only from {@code REQUESTED} or {@code IN_PREPARATION} (RF-TRA-06). The legal
 * source states are data — a {@link Map} constant a test can enumerate exhaustively
 * (RNF-MAN-01) — never a chain of {@code if}s.
 */
public final class TransferStateMachine {

	private static final Map<TransferStatus, Set<TransferTransition>> LEGAL_SOURCES = Map.of(
			TransferStatus.REQUESTED,
			Set.of(TransferTransition.APPROVE, TransferTransition.REJECT, TransferTransition.CANCEL),
			TransferStatus.IN_PREPARATION, Set.of(TransferTransition.DISPATCH, TransferTransition.CANCEL),
			TransferStatus.IN_TRANSIT, Set.of(TransferTransition.RECEIVE),
			TransferStatus.RECEIVED, Set.of(),
			TransferStatus.RECEIVED_WITH_DISCREPANCY, Set.of(),
			TransferStatus.CANCELLED, Set.of());

	private TransferStateMachine() {
	}

	/**
	 * @throws InvalidTransferStateException {@code transition} is not legal from {@code current}
	 */
	public static void require(TransferStatus current, TransferTransition transition) {
		if (!LEGAL_SOURCES.getOrDefault(current, Set.of()).contains(transition)) {
			throw new InvalidTransferStateException(
					"transition " + transition + " is not legal from state " + current);
		}
	}
}
