package com.optiplant.inventory.transfers.application.service;

import com.optiplant.inventory.transfers.application.port.in.DispatchTransferUseCase;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.CarrierName;
import com.optiplant.inventory.transfers.domain.model.DispatchDetails;
import com.optiplant.inventory.transfers.domain.model.DispatchLine;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy.Side;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy.DispatchOperation;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy.DispatchPlan;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy.DispatchPlanLine;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.route.RouteLeadTimePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.InTransitDirection;
import com.optiplant.inventory.shared.stock.InTransitShiftCommand;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates dispatch (CU-TRA-03), the first real consumer of {@link StockMutationPort}
 * (contract §1): lock the transfer row, apply the state transition, precompute
 * {@code estimated_arrival_at} through {@link RouteLeadTimePort} (P-11), then replay the §7.1
 * deterministic lock-ordered plan against the stock port — {@code TRANSFER_OUT} on the origin,
 * {@code shiftInTransit} increment on the destination, per item, atomically (R-11, T-01).
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc.
 */
@Service
public class DispatchTransferService implements DispatchTransferUseCase {

	private final TransferRepositoryPort transferRepository;
	private final TransferReferencePort referencePort;
	private final StockMutationPort stockMutationPort;
	private final RouteLeadTimePort routeLeadTimePort;
	private final AuditWritePort auditWritePort;

	public DispatchTransferService(TransferRepositoryPort transferRepository, TransferReferencePort referencePort,
			StockMutationPort stockMutationPort, RouteLeadTimePort routeLeadTimePort, AuditWritePort auditWritePort) {
		this.transferRepository = transferRepository;
		this.referencePort = referencePort;
		this.stockMutationPort = stockMutationPort;
		this.routeLeadTimePort = routeLeadTimePort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional
	public TransferDetail dispatch(AuthenticatedPrincipal actor, UUID transferExternalId, DispatchCommand command) {
		Transfer transfer = transferRepository.lockForUpdate(transferExternalId)
				.orElseThrow(() -> new TransferNotFoundException(transferExternalId));
		TransferAccessPolicy.assertSide(actor, transfer, Side.ORIGIN);

		List<DispatchLine> lines = command.items().stream()
				.map(line -> new DispatchLine(line.itemExternalId(), line.dispatchedQuantity()))
				.toList();

		Instant dispatchedAt = Instant.now();
		Instant estimatedArrivalAt = resolveEstimatedArrival(transfer, command, dispatchedAt);
		DispatchDetails details = new DispatchDetails(new CarrierName(command.carrierName()),
				command.trackingNumber(), dispatchedAt, estimatedArrivalAt, actor.userId());

		Transfer dispatched = transfer.dispatch(details, lines, dispatchedAt);

		DispatchPlan plan = TransferDispatchPolicy.plan(transfer.originBranchExternalId(),
				transfer.destinationBranchExternalId(), transfer.items(), lines);
		for (DispatchPlanLine planLine : plan.lines()) {
			applyPlanLine(planLine, transferExternalId, actor.userId());
		}

		Transfer saved = transferRepository.save(dispatched);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), saved.originBranchExternalId(),
				"DISPATCH_TRANSFER", "transfers", saved.externalId().toString(), null, null, null));

		return TransferDetailAssembler.toDetail(saved, referencePort);
	}

	private Instant resolveEstimatedArrival(Transfer transfer, DispatchCommand command, Instant dispatchedAt) {
		Optional<Duration> leadTime = routeLeadTimePort.estimatedLeadTime(transfer.originBranchExternalId(),
				transfer.destinationBranchExternalId());
		return leadTime.map(dispatchedAt::plus).orElse(command.estimatedArrivalAt());
	}

	private void applyPlanLine(DispatchPlanLine planLine, UUID transferExternalId, UUID actorUserExternalId) {
		if (planLine.operation() == DispatchOperation.STOCK_OUT) {
			stockMutationPort.applyMovement(new StockMutationCommand(planLine.branchExternalId(),
					planLine.productExternalId(), StockMovementType.TRANSFER_OUT, planLine.quantity().value(), null,
					"TRANSFER", transferExternalId.toString(), null, actorUserExternalId));
		} else {
			stockMutationPort.shiftInTransit(new InTransitShiftCommand(planLine.branchExternalId(),
					planLine.productExternalId(), planLine.quantity().value(), InTransitDirection.INCREMENT,
					actorUserExternalId));
		}
	}
}
