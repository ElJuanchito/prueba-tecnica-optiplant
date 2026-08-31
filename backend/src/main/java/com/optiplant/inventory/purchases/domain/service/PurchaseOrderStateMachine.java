package com.optiplant.inventory.purchases.domain.service;

import com.optiplant.inventory.purchases.domain.exception.InvalidOrderStateException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderTransition;
import java.util.Map;
import java.util.Set;

/**
 * The only authority on R-10, R-11 and R-14 (design §3.3), mirroring {@code TransferStateMachine}:
 * a {@link Map} constant a test enumerates exhaustively (RNF-MAN-01), never a chain of {@code if}s.
 *
 * <pre>
 * PENDING            -&gt; { EDIT, APPROVE, CANCEL }        RECEIVED  -&gt; { }   // terminal
 * APPROVED           -&gt; { RECEIVE, CANCEL }              CANCELLED -&gt; { }   // terminal
 * PARTIALLY_RECEIVED -&gt; { RECEIVE, CANCEL }
 * </pre>
 *
 * <p>The service locks the {@code purchase_orders} row before reading {@code status}, so the state
 * a transition validates against is the state it commits against (F-5, design §10 trap 3).
 */
public final class PurchaseOrderStateMachine {

	private static final Map<PurchaseOrderStatus, Set<PurchaseOrderTransition>> LEGAL_SOURCES = Map.of(
			PurchaseOrderStatus.PENDING,
			Set.of(PurchaseOrderTransition.EDIT, PurchaseOrderTransition.APPROVE, PurchaseOrderTransition.CANCEL),
			PurchaseOrderStatus.APPROVED,
			Set.of(PurchaseOrderTransition.RECEIVE, PurchaseOrderTransition.CANCEL),
			PurchaseOrderStatus.PARTIALLY_RECEIVED,
			Set.of(PurchaseOrderTransition.RECEIVE, PurchaseOrderTransition.CANCEL),
			PurchaseOrderStatus.RECEIVED, Set.of(),
			PurchaseOrderStatus.CANCELLED, Set.of());

	private PurchaseOrderStateMachine() {
	}

	/**
	 * @throws InvalidOrderStateException {@code transition} is not legal from {@code current}
	 */
	public static void require(PurchaseOrderStatus current, PurchaseOrderTransition transition) {
		if (!LEGAL_SOURCES.getOrDefault(current, Set.of()).contains(transition)) {
			throw new InvalidOrderStateException(
					"transition " + transition + " is not legal from state " + current);
		}
	}

	/** {@code true} when {@code transition} is legal from {@code current}. */
	public static boolean allows(PurchaseOrderStatus current, PurchaseOrderTransition transition) {
		return LEGAL_SOURCES.getOrDefault(current, Set.of()).contains(transition);
	}
}
