package com.optiplant.inventory.transfers.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.transfers.application.port.in.DispatchTransferUseCase.DispatchCommand;
import com.optiplant.inventory.transfers.application.port.in.DispatchTransferUseCase.DispatchLineCommand;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferNumber;
import com.optiplant.inventory.transfers.domain.model.TransferPriority;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.route.RouteLeadTimePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationPort;
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
 * Unit tests for {@link DispatchTransferService} (CU-TRA-03): invocable only from the origin
 * (R-10), stock effects applied through {@link StockMutationPort}, and every dispatch writes an
 * audit entry scoped to the origin branch (T-03, task 1.11).
 */
@ExtendWith(MockitoExtension.class)
class DispatchTransferServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	@Mock
	private TransferRepositoryPort transferRepository;
	@Mock
	private TransferReferencePort referencePort;
	@Mock
	private StockMutationPort stockMutationPort;
	@Mock
	private RouteLeadTimePort routeLeadTimePort;
	@Mock
	private AuditWritePort auditWritePort;

	private DispatchTransferService service;
	private UUID origin;
	private UUID destination;
	private UUID itemId;

	@BeforeEach
	void setUp() {
		service = new DispatchTransferService(transferRepository, referencePort, stockMutationPort, routeLeadTimePort,
				auditWritePort);
		origin = UUID.randomUUID();
		destination = UUID.randomUUID();
		itemId = UUID.randomUUID();
		lenient().when(referencePort.findBranches(any())).thenReturn(Map.of());
		lenient().when(referencePort.findProducts(any())).thenReturn(Map.of());
	}

	@Test
	void dispatchingWritesAnAuditEntryScopedToTheOriginBranchAndAppliesStockEffects() {
		Transfer preparing = preparingTransfer();
		when(transferRepository.lockForUpdate(preparing.externalId())).thenReturn(Optional.of(preparing));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(routeLeadTimePort.estimatedLeadTime(origin, destination)).thenReturn(Optional.empty());
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.operator",
				Role.OPERATOR, origin);

		DispatchCommand command = new DispatchCommand("DHL", "TRACK-1", Instant.parse("2026-08-30T00:00:00Z"),
				List.of(new DispatchLineCommand(itemId, new BigDecimal("100"))));
		service.dispatch(atOrigin, preparing.externalId(), command);

		verify(auditWritePort).record(new AuditEntryCommand(atOrigin.userId(), origin, "DISPATCH_TRANSFER",
				"transfers", preparing.externalId().toString(), null, null, null));
		verify(stockMutationPort).applyMovement(argThat(cmd -> cmd.branchExternalId().equals(origin)
				&& cmd.movementType() == StockMovementType.TRANSFER_OUT && cmd.unitCost() == null));
		verify(stockMutationPort).shiftInTransit(argThat(cmd -> cmd.branchExternalId().equals(destination)));
	}

	@Test
	void dispatchingFromTheDestinationBranchIsRefusedAndNoStockIsTouched() {
		Transfer preparing = preparingTransfer();
		when(transferRepository.lockForUpdate(preparing.externalId())).thenReturn(Optional.of(preparing));
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.operator",
				Role.OPERATOR, destination);

		DispatchCommand command = new DispatchCommand("DHL", "TRACK-1", null,
				List.of(new DispatchLineCommand(itemId, new BigDecimal("100"))));

		assertThatThrownBy(() -> service.dispatch(atDestination, preparing.externalId(), command))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
		verify(stockMutationPort, never()).applyMovement(any());
		verify(stockMutationPort, never()).shiftInTransit(any());
	}

	@Test
	void dispatchingARequestedTransferIsRefusedByTheStateMachine() {
		Transfer requested = requestedTransfer();
		when(transferRepository.lockForUpdate(requested.externalId())).thenReturn(Optional.of(requested));
		AuthenticatedPrincipal atOrigin = new AuthenticatedPrincipal(UUID.randomUUID(), "origin.operator",
				Role.OPERATOR, origin);

		DispatchCommand command = new DispatchCommand("DHL", "TRACK-1", null,
				List.of(new DispatchLineCommand(itemId, new BigDecimal("100"))));

		assertThatThrownBy(() -> service.dispatch(atOrigin, requested.externalId(), command))
				.isInstanceOf(InvalidTransferStateException.class);
		verify(stockMutationPort, never()).applyMovement(any());
	}

	private Transfer preparingTransfer() {
		TransferItem item = TransferItem.requested(itemId, UUID.randomUUID(), new TransferQuantity(new BigDecimal("100")));
		return new Transfer(UUID.randomUUID(), new TransferNumber("TRF-2026-0001"), TransferStatus.IN_PREPARATION,
				origin, destination, UUID.randomUUID(), null, null, null, null, null, null, null,
				TransferNotes.empty(TransferPriority.STANDARD), NOW, NOW, List.of(item));
	}

	private Transfer requestedTransfer() {
		TransferItem item = TransferItem.requested(itemId, UUID.randomUUID(), new TransferQuantity(new BigDecimal("100")));
		return new Transfer(UUID.randomUUID(), new TransferNumber("TRF-2026-0002"), TransferStatus.REQUESTED, origin,
				destination, UUID.randomUUID(), null, null, null, null, null, null, null,
				TransferNotes.empty(TransferPriority.STANDARD), NOW, NOW, List.of(item));
	}
}
