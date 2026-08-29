package com.optiplant.inventory.transfers.application.service;

import com.optiplant.inventory.transfers.application.port.in.ReceiveTransferUseCase;
import com.optiplant.inventory.transfers.application.port.out.TransferAlertPublisherPort;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.ReceiptLine;
import com.optiplant.inventory.transfers.domain.model.ReceiptOutcome;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy.Side;
import com.optiplant.inventory.transfers.domain.service.TransferReceiptPolicy;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.InTransitDirection;
import com.optiplant.inventory.shared.stock.InTransitShiftCommand;
import com.optiplant.inventory.shared.stock.OutboundValuationPort;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates receipt, complete or partial (CU-TRA-04, CU-TRA-05, contract §2.4): one physical
 * act resolving to {@code RECEIVED} or {@code RECEIVED_WITH_DISCREPANCY}. Per item:
 * {@code TRANSFER_IN} on the destination for the received quantity only, valued at the matching
 * {@code TRANSFER_OUT}'s unit cost (R-20, D-2), and a full-dispatched-quantity decrement of
 * {@code in_transit_stock} so no phantom balance survives (R-16). Rows are touched sorted by
 * product (§7.1 — both operations share the destination row).
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc.
 */
@Service
public class ReceiveTransferService implements ReceiveTransferUseCase {

	private final TransferRepositoryPort transferRepository;
	private final TransferReferencePort referencePort;
	private final StockMutationPort stockMutationPort;
	private final OutboundValuationPort outboundValuationPort;
	private final AuditWritePort auditWritePort;
	private final TransferAlertPublisherPort alertPublisherPort;

	public ReceiveTransferService(TransferRepositoryPort transferRepository, TransferReferencePort referencePort,
			StockMutationPort stockMutationPort, OutboundValuationPort outboundValuationPort,
			AuditWritePort auditWritePort, TransferAlertPublisherPort alertPublisherPort) {
		this.transferRepository = transferRepository;
		this.referencePort = referencePort;
		this.stockMutationPort = stockMutationPort;
		this.outboundValuationPort = outboundValuationPort;
		this.auditWritePort = auditWritePort;
		this.alertPublisherPort = alertPublisherPort;
	}

	@Override
	@Transactional
	public TransferDetail receive(AuthenticatedPrincipal actor, UUID transferExternalId, ReceiptCommand command) {
		Transfer transfer = transferRepository.lockForUpdate(transferExternalId)
				.orElseThrow(() -> new TransferNotFoundException(transferExternalId));
		TransferAccessPolicy.assertSide(actor, transfer, Side.DESTINATION);

		List<TransferReceiptPolicy.ReceiptLineCommand> lines = command.items().stream()
				.map(line -> new TransferReceiptPolicy.ReceiptLineCommand(line.itemExternalId(),
						line.receivedQuantity(), line.discrepancyReason()))
				.toList();
		ReceiptOutcome outcome = TransferReceiptPolicy.apply(transfer.items(), lines);

		Instant now = Instant.now();
		Transfer received = transfer.receive(outcome, actor.userId(), now);

		Map<UUID, BigDecimal> unitCosts = outboundValuationPort.outboundUnitCosts(transfer.originBranchExternalId(),
				"TRANSFER", transferExternalId.toString());

		List<ReceiptLine> sortedLines = outcome.lines().stream()
				.sorted(Comparator.comparing(ReceiptLine::productExternalId))
				.toList();
		Map<UUID, TransferItem> dispatchedByItem = transfer.items().stream()
				.collect(java.util.stream.Collectors.toMap(TransferItem::externalId, java.util.function.Function.identity()));

		for (ReceiptLine line : sortedLines) {
			applyReceiptLine(line, dispatchedByItem.get(line.itemExternalId()), transfer.destinationBranchExternalId(),
					transferExternalId, unitCosts, actor.userId());
		}

		Transfer saved = transferRepository.save(received);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), saved.destinationBranchExternalId(),
				"RECEIVE_TRANSFER", "transfers", saved.externalId().toString(), null, null, null));

		if (outcome.hasDiscrepancy()) {
			publishDiscrepancyAlerts(saved, now);
		}

		return TransferDetailAssembler.toDetail(saved, referencePort);
	}

	private void applyReceiptLine(ReceiptLine line, TransferItem dispatchedItem, UUID destinationBranchExternalId,
			UUID transferExternalId, Map<UUID, BigDecimal> unitCosts, UUID actorUserExternalId) {
		if (line.receivedQuantity().value().signum() > 0) {
			BigDecimal unitCost = unitCosts.get(line.productExternalId());
			stockMutationPort.applyMovement(new StockMutationCommand(destinationBranchExternalId,
					line.productExternalId(), StockMovementType.TRANSFER_IN, line.receivedQuantity().value(),
					unitCost, "TRANSFER", transferExternalId.toString(), null, actorUserExternalId));
		}
		BigDecimal dispatchedQuantity = dispatchedItem == null ? BigDecimal.ZERO
				: dispatchedItem.dispatchedQuantity().value();
		if (dispatchedQuantity.signum() > 0) {
			stockMutationPort.shiftInTransit(new InTransitShiftCommand(destinationBranchExternalId,
					line.productExternalId(), dispatchedQuantity, InTransitDirection.DECREMENT, actorUserExternalId));
		}
	}

	private void publishDiscrepancyAlerts(Transfer transfer, Instant occurredAt) {
		String message = "Transfer " + transfer.number().value() + " received with a discrepancy";
		alertPublisherPort.publish(new OperationalAlertRaised(transfer.originBranchExternalId(),
				AlertType.TRANSFER_DISCREPANCY, AlertSeverity.CRITICAL, transfer.externalId().toString(), message,
				occurredAt));
		alertPublisherPort.publish(new OperationalAlertRaised(transfer.destinationBranchExternalId(),
				AlertType.TRANSFER_DISCREPANCY, AlertSeverity.CRITICAL, transfer.externalId().toString(), message,
				occurredAt));
	}
}
