package com.optiplant.inventory.purchases.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.purchases.domain.exception.InvalidOrderStateException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderTransition;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R-11: every state x transition, both terminal states, plus R-10's {@code EDIT} refusal once an
 * order leaves {@code PENDING}.
 */
class PurchaseOrderStateMachineTest {

	private static final Map<PurchaseOrderStatus, Set<PurchaseOrderTransition>> LEGAL = Map.of(
			PurchaseOrderStatus.PENDING,
			Set.of(PurchaseOrderTransition.EDIT, PurchaseOrderTransition.APPROVE, PurchaseOrderTransition.CANCEL),
			PurchaseOrderStatus.APPROVED,
			Set.of(PurchaseOrderTransition.RECEIVE, PurchaseOrderTransition.CANCEL),
			PurchaseOrderStatus.PARTIALLY_RECEIVED,
			Set.of(PurchaseOrderTransition.RECEIVE, PurchaseOrderTransition.CANCEL),
			PurchaseOrderStatus.RECEIVED, Set.of(),
			PurchaseOrderStatus.CANCELLED, Set.of());

	@Test
	@DisplayName("R-11: the full state x transition matrix, terminals included")
	void fullMatrix() {
		for (PurchaseOrderStatus state : PurchaseOrderStatus.values()) {
			for (PurchaseOrderTransition transition : PurchaseOrderTransition.values()) {
				boolean expectedLegal = LEGAL.get(state).contains(transition);

				assertThat(PurchaseOrderStateMachine.allows(state, transition))
						.as("%s -> %s", state, transition)
						.isEqualTo(expectedLegal);

				if (expectedLegal) {
					assertThatCode(() -> PurchaseOrderStateMachine.require(state, transition))
							.as("%s -> %s must be allowed", state, transition)
							.doesNotThrowAnyException();
				} else {
					assertThatThrownBy(() -> PurchaseOrderStateMachine.require(state, transition))
							.as("%s -> %s must be refused", state, transition)
							.isInstanceOf(InvalidOrderStateException.class);
				}
			}
		}
	}

	@Test
	@DisplayName("R-11: RECEIVED and CANCELLED accept no transition")
	void terminalsAreClosed() {
		for (PurchaseOrderTransition transition : PurchaseOrderTransition.values()) {
			assertThat(PurchaseOrderStateMachine.allows(PurchaseOrderStatus.RECEIVED, transition)).isFalse();
			assertThat(PurchaseOrderStateMachine.allows(PurchaseOrderStatus.CANCELLED, transition)).isFalse();
		}
		assertThat(PurchaseOrderStatus.RECEIVED.isTerminal()).isTrue();
		assertThat(PurchaseOrderStatus.CANCELLED.isTerminal()).isTrue();
	}

	@Test
	@DisplayName("R-10: EDIT is refused from every state other than PENDING")
	void editRefusedOncePendingIsLeft() {
		assertThatCode(() -> PurchaseOrderStateMachine.require(PurchaseOrderStatus.PENDING,
				PurchaseOrderTransition.EDIT)).doesNotThrowAnyException();

		for (PurchaseOrderStatus state : new PurchaseOrderStatus[] { PurchaseOrderStatus.APPROVED,
				PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CANCELLED }) {
			assertThatThrownBy(() -> PurchaseOrderStateMachine.require(state, PurchaseOrderTransition.EDIT))
					.as("EDIT from %s", state)
					.isInstanceOf(InvalidOrderStateException.class);
		}
	}
}
