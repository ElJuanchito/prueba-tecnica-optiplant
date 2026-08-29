package com.optiplant.inventory.transfers.domain.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.transfers.domain.model.TransferTransition;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link TransferStateMachine} — the only authority on R-01 (RNF-MAN-01): every
 * {@link TransferStatus} &times; {@link TransferTransition} pair, legal and illegal.
 * {@code REQUESTED -> IN_PREPARATION -> IN_TRANSIT -> RECEIVED | RECEIVED_WITH_DISCREPANCY} MUST
 * NOT be skipped; {@code CANCELLED} is reachable only from {@code REQUESTED} or
 * {@code IN_PREPARATION} (R-14, R-22).
 */
class TransferStateMachineTest {

	/** The legal source states, hand-authored from the contract — independent of the production map. */
	private static final Map<TransferStatus, Set<TransferTransition>> EXPECTED_LEGAL = new EnumMap<>(TransferStatus.class);

	static {
		EXPECTED_LEGAL.put(TransferStatus.REQUESTED,
				EnumSet.of(TransferTransition.APPROVE, TransferTransition.REJECT, TransferTransition.CANCEL));
		EXPECTED_LEGAL.put(TransferStatus.IN_PREPARATION,
				EnumSet.of(TransferTransition.DISPATCH, TransferTransition.CANCEL));
		EXPECTED_LEGAL.put(TransferStatus.IN_TRANSIT, EnumSet.of(TransferTransition.RECEIVE));
		EXPECTED_LEGAL.put(TransferStatus.RECEIVED, EnumSet.noneOf(TransferTransition.class));
		EXPECTED_LEGAL.put(TransferStatus.RECEIVED_WITH_DISCREPANCY, EnumSet.noneOf(TransferTransition.class));
		EXPECTED_LEGAL.put(TransferStatus.CANCELLED, EnumSet.noneOf(TransferTransition.class));
	}

	static Stream<Arguments> everyStateAndTransition() {
		return Stream.of(TransferStatus.values())
				.flatMap(status -> Stream.of(TransferTransition.values())
						.map(transition -> Arguments.of(status, transition, EXPECTED_LEGAL.get(status).contains(transition))));
	}

	@ParameterizedTest(name = "{0} x {1} -> legal={2}")
	@MethodSource("everyStateAndTransition")
	void enumeratesEveryPairLegalOrIllegal(TransferStatus status, TransferTransition transition, boolean legal) {
		if (legal) {
			assertThatCode(() -> TransferStateMachine.require(status, transition)).doesNotThrowAnyException();
		} else {
			assertThatThrownBy(() -> TransferStateMachine.require(status, transition))
					.isInstanceOf(InvalidTransferStateException.class);
		}
	}

	@org.junit.jupiter.api.Test
	void dispatchFromRequestedIsRefused() {
		// R-14: the mandatory ordering cannot be skipped by dispatching straight from REQUESTED.
		assertThatThrownBy(() -> TransferStateMachine.require(TransferStatus.REQUESTED, TransferTransition.DISPATCH))
				.isInstanceOf(InvalidTransferStateException.class);
	}

	@org.junit.jupiter.api.Test
	void cancellationFromInTransitIsRefused() {
		// R-22: goods in motion are resolved by receipt, not cancellation.
		assertThatThrownBy(() -> TransferStateMachine.require(TransferStatus.IN_TRANSIT, TransferTransition.CANCEL))
				.isInstanceOf(InvalidTransferStateException.class);
	}

	@org.junit.jupiter.api.Test
	void terminalStatesAcceptNoTransition() {
		for (TransferStatus terminal : Set.of(TransferStatus.RECEIVED, TransferStatus.RECEIVED_WITH_DISCREPANCY,
				TransferStatus.CANCELLED)) {
			for (TransferTransition transition : TransferTransition.values()) {
				assertThatThrownBy(() -> TransferStateMachine.require(terminal, transition))
						.isInstanceOf(InvalidTransferStateException.class);
			}
		}
	}
}
