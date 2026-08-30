package com.optiplant.inventory.transfers.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.transfers.application.port.in.ReceiveTransferUseCase.ReceiptCommand;
import com.optiplant.inventory.transfers.application.port.in.ReceiveTransferUseCase.ReceiptLineCommand;
import com.optiplant.inventory.transfers.application.port.out.TransferAlertPublisherPort;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.model.SettledQuantity;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferNumber;
import com.optiplant.inventory.transfers.domain.model.TransferPriority;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import com.optiplant.inventory.shared.stock.OutboundValuationPort;
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
 * Unit tests for {@link ReceiveTransferService} (CU-TRA-04, CU-TRA-05): one physical act
 * resolving to {@code RECEIVED} or {@code RECEIVED_WITH_DISCREPANCY}; audit on every mutation and
 * the discrepancy alert published exactly twice, once per branch, and only on a shortfall
 * (R-18, task 1.11).
 */
@ExtendWith(MockitoExtension.class)
class ReceiveTransferServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	@Mock
	private TransferRepositoryPort transferRepository;
	@Mock
	private TransferReferencePort referencePort;
	@Mock
	private StockMutationPort stockMutationPort;
	@Mock
	private OutboundValuationPort outboundValuationPort;
	@Mock
	private AuditWritePort auditWritePort;
	@Mock
	private TransferAlertPublisherPort alertPublisherPort;

	private ReceiveTransferService service;
	private UUID origin;
	private UUID destination;
	private UUID itemId;
	private UUID productId;

	@BeforeEach
	void setUp() {
		service = new ReceiveTransferService(transferRepository, referencePort, stockMutationPort,
				outboundValuationPort, auditWritePort, alertPublisherPort);
		origin = UUID.randomUUID();
		destination = UUID.randomUUID();
		itemId = UUID.randomUUID();
		productId = UUID.randomUUID();
		when(referencePort.findBranches(any())).thenReturn(Map.of());
		when(referencePort.findProducts(any())).thenReturn(Map.of());
		when(outboundValuationPort.outboundUnitCosts(any(), any(), any())).thenReturn(Map.of(productId, BigDecimal.TEN));
	}

	@Test
	void aFullReceiptWritesAuditAndPublishesNoAlert() {
		Transfer inTransit = inTransitTransfer("100");
		when(transferRepository.lockForUpdate(inTransit.externalId())).thenReturn(Optional.of(inTransit));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.operator",
				Role.OPERATOR, destination);

		ReceiptCommand command = new ReceiptCommand(List.of(new ReceiptLineCommand(itemId, new BigDecimal("100"), null)));
		service.receive(atDestination, inTransit.externalId(), command);

		verify(auditWritePort).record(new AuditEntryCommand(atDestination.userId(), destination, "RECEIVE_TRANSFER",
				"transfers", inTransit.externalId().toString(), null, null, null));
		verify(alertPublisherPort, never()).publish(any());
	}

	@Test
	void aShortfallPublishesTheDiscrepancyAlertExactlyOncePerBranch() {
		Transfer inTransit = inTransitTransfer("100");
		when(transferRepository.lockForUpdate(inTransit.externalId())).thenReturn(Optional.of(inTransit));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.operator",
				Role.OPERATOR, destination);

		ReceiptCommand command = new ReceiptCommand(
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("90"), "damaged in transit")));
		service.receive(atDestination, inTransit.externalId(), command);

		verify(alertPublisherPort, times(2)).publish(any());
		verify(alertPublisherPort).publish(argThat(event -> event.branchExternalId().equals(origin)
				&& event.alertType() == AlertType.TRANSFER_DISCREPANCY));
		verify(alertPublisherPort).publish(argThat(event -> event.branchExternalId().equals(destination)
				&& event.alertType() == AlertType.TRANSFER_DISCREPANCY));
	}

	@Test
	void zeroReceivedOnEveryItemIsAValidReceiptAndStillPublishesTheAlertTwice() {
		Transfer inTransit = inTransitTransfer("100");
		when(transferRepository.lockForUpdate(inTransit.externalId())).thenReturn(Optional.of(inTransit));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.operator",
				Role.OPERATOR, destination);

		ReceiptCommand command = new ReceiptCommand(
				List.of(new ReceiptLineCommand(itemId, BigDecimal.ZERO, "total loss")));
		service.receive(atDestination, inTransit.externalId(), command);

		verify(alertPublisherPort, times(2)).publish(any());
		// A zero-received line never crosses the > 0 gate, so no TRANSFER_IN movement is applied.
		verify(stockMutationPort, never()).applyMovement(argThat(cmd -> cmd.movementType() == StockMovementType.TRANSFER_IN));
	}

	@Test
	void receivingAppliesTransferInValuedAtTheDispatchUnitCostAndDecrementsInTransitByTheFullDispatchedQuantity() {
		Transfer inTransit = inTransitTransfer("100");
		when(transferRepository.lockForUpdate(inTransit.externalId())).thenReturn(Optional.of(inTransit));
		when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		AuthenticatedPrincipal atDestination = new AuthenticatedPrincipal(UUID.randomUUID(), "destination.operator",
				Role.OPERATOR, destination);

		ReceiptCommand command = new ReceiptCommand(
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("90"), "damaged in transit")));
		service.receive(atDestination, inTransit.externalId(), command);

		verify(stockMutationPort).applyMovement(argThat(cmd -> cmd.branchExternalId().equals(destination)
				&& cmd.movementType() == StockMovementType.TRANSFER_IN && cmd.quantity().compareTo(new BigDecimal("90")) == 0
				&& cmd.unitCost().compareTo(BigDecimal.TEN) == 0));
		verify(stockMutationPort).shiftInTransit(argThat(
				cmd -> cmd.branchExternalId().equals(destination) && cmd.quantity().compareTo(new BigDecimal("100")) == 0));
	}

	private Transfer inTransitTransfer(String dispatchedQuantity) {
		TransferItem requested = TransferItem.requested(itemId, productId, new TransferQuantity(new BigDecimal("100")));
		TransferItem dispatched = new TransferItem(requested.externalId(), requested.productExternalId(),
				requested.requestedQuantity(), new SettledQuantity(new BigDecimal(dispatchedQuantity)),
				requested.receivedQuantity(), requested.discrepancyQuantity(), null);
		return new Transfer(UUID.randomUUID(), new TransferNumber("TRF-2026-0001"), TransferStatus.IN_TRANSIT, origin,
				destination, UUID.randomUUID(), UUID.randomUUID(), null, null, null, NOW, NOW.plusSeconds(3600), null,
				TransferNotes.empty(TransferPriority.STANDARD), NOW, NOW, List.of(dispatched));
	}
}
