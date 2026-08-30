package com.optiplant.inventory.transfers.domain.service;

import com.optiplant.inventory.transfers.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;

/**
 * Two ordered questions, and the order is the security property (contract §5, RNF-SEC-03,
 * design §3.3).
 *
 * <ol>
 * <li><strong>Visibility</strong>: {@code ADMIN}, or the actor's branch is origin or
 * destination; otherwise {@link TransferNotFoundException} — {@code 404}, never {@code 403}, so
 * existence does not leak.</li>
 * <li><strong>Side</strong>: the actor's branch equals the side the transition requires —
 * origin, destination, or either for cancellation (R-21); otherwise
 * {@link CrossBranchAccessDeniedException}.</li>
 * </ol>
 */
public final class TransferAccessPolicy {

	private TransferAccessPolicy() {
	}

	/** Which side of the transfer an operation is scoped to (contract §5). */
	public enum Side {
		ORIGIN, DESTINATION, EITHER
	}

	/**
	 * R-05: a corporate {@code ADMIN} has no destination branch to derive for a new request.
	 *
	 * @throws BranchContextRequiredException {@code actor} is a corporate {@code ADMIN}
	 */
	public static UUID resolveDestinationBranch(AuthenticatedPrincipal actor) {
		if (actor.isCorporate()) {
			throw new BranchContextRequiredException();
		}
		return actor.branchId();
	}

	/**
	 * @throws TransferNotFoundException the transfer involves neither of the actor's branches
	 */
	public static void assertVisible(AuthenticatedPrincipal actor, Transfer transfer) {
		if (actor.role() == Role.ADMIN) {
			return;
		}
		if (!transfer.involves(actor.branchId())) {
			throw new TransferNotFoundException(transfer.externalId());
		}
	}

	/**
	 * Checks visibility first, then that {@code actor}'s branch is the side {@code side} names.
	 *
	 * @throws TransferNotFoundException the transfer involves neither of the actor's branches
	 * @throws CrossBranchAccessDeniedException the actor's branch is visible but not the required side
	 */
	public static void assertSide(AuthenticatedPrincipal actor, Transfer transfer, Side side) {
		assertVisible(actor, transfer);
		if (actor.role() == Role.ADMIN) {
			return;
		}
		UUID actorBranch = actor.branchId();
		boolean allowed = switch (side) {
			case ORIGIN -> transfer.originBranchExternalId().equals(actorBranch);
			case DESTINATION -> transfer.destinationBranchExternalId().equals(actorBranch);
			case EITHER -> transfer.originBranchExternalId().equals(actorBranch)
					|| transfer.destinationBranchExternalId().equals(actorBranch);
		};
		if (!allowed) {
			throw new CrossBranchAccessDeniedException();
		}
	}
}
