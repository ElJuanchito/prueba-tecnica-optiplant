package com.optiplant.inventory.transfers.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.transfers.application.port.in.ReviewTransferUseCase.ApprovalCommand;
import com.optiplant.inventory.transfers.application.port.in.ReviewTransferUseCase.ApprovedLineCommand;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException;
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
 * Unit tests for {@link ReviewTransferService} (CU-TRA-02): invocable only from the origin
 * (R-06), and every mutation writes an audit entry scoped to the origin branch (T-03, task 1.11).
 */
@ExtendWith(MockitoExtension.class)
class ReviewTransferServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	@Mock
	private TransferRepositoryPort transferRepository;
	@Mock
	private TransferReferencePort referencePort;
	@Mock
	private AuditWritePort auditWritePort;

	private ReviewTransferService service;
	private UUID origin;
	private UUID destination;
	private UUID itemId;

	@BeforeEach
	void setUp() {
		service = new ReviewTransferService(transferRepository, referencePort, auditWritePort);
		origin = UUID.randomUUID();
		destination = UUID.randomUUID();
		itemId = UUID.randomUUID();
		lenient().when(referencePort.findBranches(any())).thenReturn(Map.of());
		lenient().when(referencePort.findProducts(any())).thenReturn(Map.of());
	}

	@Test
	void approvingWritesAnAuditEntryScopedToTheOriginBranch() {
		Transfer requested = requestedTransfer();
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.manager",
				Role.BRANCH_MANAGER, origin);

		ApprovalCommand command = new ApprovalCommand(List.of(new ApprovedLineCommand(itemId, new BigDecimal("60"))),
				null);
		service.approve(atOrigin, requested.externalId(), command);

		verify(auditWritePort).record(new AuditEntryCommand(atOrigin.userId(), origin, "APPROVE_TRANSFER", "transfers",
				requested.externalId().toString(), null, null, null));
	}

	@Test
	void rejectingWritesAnAuditEntryScopedToTheOriginBranch() {
		Transfer requested = requestedTransfer();
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.manager",
				Role.BRANCH_MANAGER, origin);

		service.reject(atOrigin, requested.externalId(), "no longer needed");

		verify(auditWritePort).record(new AuditEntryCommand(atOrigin.userId(), origin, "REJECT_TRANSFER", "transfers",
				requested.externalId().toString(), null, null, null));
	}

	@Test
	void approvingFromTheDestinationBranchIsRefusedAndNothingIsWritten() {
		Transfer requested = requestedTransfer();
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.manager",
				Role.BRANCH_MANAGER, destination);

		ApprovalCommand command = new ApprovalCommand(List.of(new ApprovedLineCommand(itemId, new BigDecimal("60"))),
				null);

		assertThatThrownBy(() -> service.approve(atDestination, requested.externalId(), command))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
		verify(transferRepository, never()).save(any());
	}

	@Test
	void anUnknownTransferIsNotFound() {
		UUID unknown = UUID.randomUUID();
		when(transferRepository.lockForUpdate(unknown)).thenReturn(Optional.empty());
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "manager",
				Role.BRANCH_MANAGER, origin);

		assertThatThrownBy(() -> service.reject(atOrigin, unknown, "reason"))
				.isInstanceOf(TransferNotFoundException.class);
	}

	private Transfer requestedTransfer() {
		TransferItem item = TransferItem.requested(itemId, UUID.randomUUID(), new TransferQuantity(new BigDecimal("100")));
		return new Transfer(UUID.randomUUID(), new TransferNumber("TRF-2026-0001"), TransferStatus.REQUESTED, origin,
				destination, UUID.randomUUID(), null, null, null, null, null, null, null,
				TransferNotes.empty(TransferPriority.STANDARD), NOW, NOW, List.of(item));
	}
}
