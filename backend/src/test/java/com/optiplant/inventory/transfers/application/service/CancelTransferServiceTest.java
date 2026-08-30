package com.optiplant.inventory.transfers.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferNumber;
import com.optiplant.inventory.transfers.domain.model.TransferPriority;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CancelTransferService} (CU-TRA-06): open to a manager of either side
 * (R-21), refused once {@code IN_TRANSIT} (R-22), and audited against the transfer's origin
 * regardless of which side cancelled (T-03, task 1.11).
 */
@ExtendWith(MockitoExtension.class)
class CancelTransferServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	@Mock
	private TransferRepositoryPort transferRepository;
	@Mock
	private TransferReferencePort referencePort;
	@Mock
	private AuditWritePort auditWritePort;

	private CancelTransferService service;
	private UUID origin;
	private UUID destination;

	@BeforeEach
	void setUp() {
		service = new CancelTransferService(transferRepository, referencePort, auditWritePort);
		origin = UUID.randomUUID();
		destination = UUID.randomUUID();
		lenient().when(referencePort.findBranches(any())).thenReturn(Map.of());
		lenient().when(referencePort.findProducts(any())).thenReturn(Map.of());
	}

	@Test
	void theDestinationBranchMayAlsoCancelAndTheAuditIsScopedToTheOrigin() {
		Transfer requested = requestedTransfer(TransferStatus.REQUESTED);
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.manager",
				Role.BRANCH_MANAGER, destination);

		service.cancel(atDestination, requested.externalId(), "requester withdrew");

		verify(auditWritePort).record(new AuditEntryCommand(atDestination.userId(), origin, "CANCEL_TRANSFER",
				"transfers", requested.externalId().toString(), null, null, null));
	}

	@Test
	void theOriginBranchMayDeclineAndTheAuditIsScopedToTheOrigin() {
		Transfer requested = requestedTransfer(TransferStatus.IN_PREPARATION);
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.manager",
				Role.BRANCH_MANAGER, origin);

		service.cancel(atOrigin, requested.externalId(), "no longer needed");

		verify(auditWritePort).record(new AuditEntryCommand(atOrigin.userId(), origin, "CANCEL_TRANSFER", "transfers",
				requested.externalId().toString(), null, null, null));
	}

	@Test
	void aThirdBranchCannotCancel() {
		Transfer requested = requestedTransfer(TransferStatus.REQUESTED);
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		AuthenticatedPrincipal thirdBranch = new AuthenticatedPrincipal(UUID.randomUUID(), "manager",
				Role.BRANCH_MANAGER, UUID.randomUUID());

		assertThatThrownBy(() -> service.cancel(thirdBranch, requested.externalId(), "reason"))
				.isInstanceOf(TransferNotFoundException.class);
		verify(transferRepository, never()).save(any());
	}

	@Test
	void anInTransitTransferCannotBeCancelled() {
		Transfer inTransit = requestedTransfer(TransferStatus.IN_TRANSIT);
		when(transferRepository.lockForUpdate(inTransit.externalId())).thenReturn(Optional.of(inTransit));
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.manager",
				Role.BRANCH_MANAGER, origin);

		assertThatThrownBy(() -> service.cancel(atOrigin, inTransit.externalId(), "reason"))
				.isInstanceOf(InvalidTransferStateException.class);
		verify(transferRepository, never()).save(any());
	}

	@Test
	void anAdminCancelsRegardlessOfBranch() {
		// R-21's EITHER side matches TransferAccessPolicy's visibility gate exactly for a
		// non-admin (origin or destination), so CrossBranchAccessDeniedException is unreachable
		// through cancel — a branch that fails the side check has already failed visibility.
		// An ADMIN instead bypasses both checks entirely (§5).
		Transfer requested = requestedTransfer(TransferStatus.REQUESTED);
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, null);

		service.cancel(admin, requested.externalId(), "corporate decision");

		verify(auditWritePort).record(new AuditEntryCommand(admin.userId(), origin, "CANCEL_TRANSFER", "transfers",
				requested.externalId().toString(), null, null, null));
	}

	private Transfer requestedTransfer(TransferStatus status) {
		TransferItem item = TransferItem.requested(UUID.randomUUID(), UUID.randomUUID(),
				new TransferQuantity(new BigDecimal("100")));
		return new Transfer(UUID.randomUUID(), new TransferNumber("TRF-2026-0001"), status, origin, destination,
				UUID.randomUUID(), null, null, null, null, null, null, null,
				TransferNotes.empty(TransferPriority.STANDARD), NOW, NOW, List.of(item));
	}
}
