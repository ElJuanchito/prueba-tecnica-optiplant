package com.optiplant.inventory.transfers.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferNumber;
import com.optiplant.inventory.transfers.domain.model.TransferPriority;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy.Side;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransferAccessPolicy} (contract §5, RNF-SEC-03): visibility is checked
 * before side, so a third branch always resolves to {@code 404}, never {@code 403} (R-03/R-05/
 * R-06/R-21).
 */
class TransferAccessPolicyTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private static Transfer transferBetween(UUID origin, UUID destination) {
		return new Transfer(UUID.randomUUID(), new TransferNumber("TRF-2026-0001"), TransferStatus.REQUESTED, origin,
				destination, UUID.randomUUID(), null, null, null, null, null, null, null,
				TransferNotes.empty(TransferPriority.STANDARD), NOW, NOW, List.of());
	}

	@Test
	void resolveDestinationBranchReturnsTheActorsOwnBranch() {
		UUID branch = UUID.randomUUID();
		AuthenticatedPrincipal manager = new AuthenticatedPrincipal(UUID.randomUUID(), "manager", Role.BRANCH_MANAGER,
				branch);

		assertThat(TransferAccessPolicy.resolveDestinationBranch(manager)).isEqualTo(branch);
	}

	@Test
	void resolveDestinationBranchRejectsACorporateAdmin() {
		AuthenticatedPrincipal corporateAdmin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN,
				null);

		assertThatThrownBy(() -> TransferAccessPolicy.resolveDestinationBranch(corporateAdmin))
				.isInstanceOf(BranchContextRequiredException.class);
	}

	@Test
	void assertVisibleAllowsTheOriginBranch() {
		UUID origin = UUID.randomUUID();
		UUID destination = UUID.randomUUID();
		Transfer transfer = transferBetween(origin, destination);
		AuthenticatedPrincipal operator = new AuthenticatedPrincipal(UUID.randomUUID(), "operator", Role.OPERATOR,
				origin);

		assertThatCode(() -> TransferAccessPolicy.assertVisible(operator, transfer)).doesNotThrowAnyException();
	}

	@Test
	void assertVisibleAllowsTheDestinationBranch() {
		UUID origin = UUID.randomUUID();
		UUID destination = UUID.randomUUID();
		Transfer transfer = transferBetween(origin, destination);
		AuthenticatedPrincipal operator = new AuthenticatedPrincipal(UUID.randomUUID(), "operator", Role.OPERATOR,
				destination);

		assertThatCode(() -> TransferAccessPolicy.assertVisible(operator, transfer)).doesNotThrowAnyException();
	}

	@Test
	void assertVisibleRejectsAThirdBranchAsNotFoundNeverForbidden() {
		Transfer transfer = transferBetween(UUID.randomUUID(), UUID.randomUUID());
		AuthenticatedPrincipal thirdBranchOperator = new AuthenticatedPrincipal(UUID.randomUUID(), "operator",
				Role.OPERATOR, UUID.randomUUID());

		assertThatThrownBy(() -> TransferAccessPolicy.assertVisible(thirdBranchOperator, transfer))
				.isInstanceOf(TransferNotFoundException.class);
	}

	@Test
	void assertVisibleAlwaysAllowsAnAdminRegardlessOfBranch() {
		Transfer transfer = transferBetween(UUID.randomUUID(), UUID.randomUUID());
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, null);

		assertThatCode(() -> TransferAccessPolicy.assertVisible(admin, transfer)).doesNotThrowAnyException();
	}

	@Test
	void assertSideOriginAllowsTheOriginBranchAndRejectsTheDestination() {
		UUID origin = UUID.randomUUID();
		UUID destination = UUID.randomUUID();
		Transfer transfer = transferBetween(origin, destination);
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.manager",
				Role.BRANCH_MANAGER, origin);
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.manager",
				Role.BRANCH_MANAGER, destination);

		assertThatCode(() -> TransferAccessPolicy.assertSide(atOrigin, transfer, Side.ORIGIN)).doesNotThrowAnyException();
		assertThatThrownBy(() -> TransferAccessPolicy.assertSide(atDestination, transfer, Side.ORIGIN))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
	}

	@Test
	void assertSideDestinationAllowsTheDestinationBranchAndRejectsTheOrigin() {
		UUID origin = UUID.randomUUID();
		UUID destination = UUID.randomUUID();
		Transfer transfer = transferBetween(origin, destination);
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.manager",
				Role.BRANCH_MANAGER, origin);
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.manager",
				Role.BRANCH_MANAGER, destination);

		assertThatCode(() -> TransferAccessPolicy.assertSide(atDestination, transfer, Side.DESTINATION))
				.doesNotThrowAnyException();
		assertThatThrownBy(() -> TransferAccessPolicy.assertSide(atOrigin, transfer, Side.DESTINATION))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
	}

	@Test
	void assertSideEitherAllowsBothOriginAndDestinationForCancellation() {
		UUID origin = UUID.randomUUID();
		UUID destination = UUID.randomUUID();
		Transfer transfer = transferBetween(origin, destination);
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.manager",
				Role.BRANCH_MANAGER, origin);
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.manager",
				Role.BRANCH_MANAGER, destination);

		assertThatCode(() -> TransferAccessPolicy.assertSide(atOrigin, transfer, Side.EITHER)).doesNotThrowAnyException();
		assertThatCode(() -> TransferAccessPolicy.assertSide(atDestination, transfer, Side.EITHER))
				.doesNotThrowAnyException();
	}

	@Test
	void assertSideRejectsAThirdBranchAsNotFoundBeforeCheckingSide() {
		Transfer transfer = transferBetween(UUID.randomUUID(), UUID.randomUUID());
		AuthenticatedPrincipal thirdBranchManager = new AuthenticatedPrincipal(UUID.randomUUID(), "manager",
				Role.BRANCH_MANAGER, UUID.randomUUID());

		assertThatThrownBy(() -> TransferAccessPolicy.assertSide(thirdBranchManager, transfer, Side.ORIGIN))
				.isInstanceOf(TransferNotFoundException.class);
	}

	@Test
	void assertSideAlwaysAllowsAnAdminRegardlessOfSide() {
		Transfer transfer = transferBetween(UUID.randomUUID(), UUID.randomUUID());
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, null);

		assertThatCode(() -> TransferAccessPolicy.assertSide(admin, transfer, Side.ORIGIN)).doesNotThrowAnyException();
		assertThatCode(() -> TransferAccessPolicy.assertSide(admin, transfer, Side.DESTINATION))
				.doesNotThrowAnyException();
	}
}
