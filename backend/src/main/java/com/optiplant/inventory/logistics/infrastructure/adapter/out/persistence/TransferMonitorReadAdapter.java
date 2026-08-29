package com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.logistics.application.port.out.TransferMonitorReadPort;
import com.optiplant.inventory.logistics.domain.model.ActiveTransferPage;
import com.optiplant.inventory.logistics.domain.model.ActiveTransferView;
import com.optiplant.inventory.logistics.domain.model.BranchReference;
import com.optiplant.inventory.logistics.domain.model.DelayedTransfer;
import com.optiplant.inventory.logistics.domain.model.DeliveryOutcome;
import com.optiplant.inventory.logistics.domain.service.DelayDetectionPolicy;
import com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence.TransferProjectionSpringDataRepository.ActiveTransferRow;
import com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence.TransferProjectionSpringDataRepository.DelayedTransferRow;
import com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence.TransferProjectionSpringDataRepository.DeliveryOutcomeRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The single {@link TransferMonitorReadPort} implementation (P-12, design §6.3) —
 * {@link TransferProjectionSpringDataRepository}'s read-only native projections, enriched here
 * with the R-25 delayed flag through {@link DelayDetectionPolicy} so the monitor and the
 * scheduled detector (R-28) can never disagree on what "delayed" means.
 */
@Component
public class TransferMonitorReadAdapter implements TransferMonitorReadPort {

	private static final String TOKEN_PREFIX = "PRIORITY:";

	private final TransferProjectionSpringDataRepository projectionRepository;
	private final LogisticsReferenceSpringDataRepository referenceRepository;

	public TransferMonitorReadAdapter(TransferProjectionSpringDataRepository projectionRepository,
			LogisticsReferenceSpringDataRepository referenceRepository) {
		this.projectionRepository = projectionRepository;
		this.referenceRepository = referenceRepository;
	}

	@Override
	public ActiveTransferPage listActive(ActiveTransferFilter filter) {
		Instant now = Instant.now();
		Long callerBranchId = filter.callerBranchExternalId() == null ? null
				: resolveBranchIdOrSentinel(filter.callerBranchExternalId());
		Page<ActiveTransferRow> page = projectionRepository.searchActive(callerBranchId, filter.status(),
				Boolean.TRUE.equals(filter.delayedOnly()), PageRequest.of(filter.page(), filter.size()));

		List<ActiveTransferView> content = page.getContent().stream().map(row -> toView(row, now)).toList();
		return new ActiveTransferPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	@Override
	public List<DeliveryOutcome> listDeliveries(DeliveryFilter filter) {
		Long callerBranchId = filter.callerBranchExternalId() == null ? null
				: resolveBranchIdOrSentinel(filter.callerBranchExternalId());
		return projectionRepository.searchDeliveries(callerBranchId, filter.from(), filter.to()).stream()
				.map(this::toOutcome).toList();
	}

	@Override
	public List<DelayedTransfer> listDelayed(Instant now) {
		return projectionRepository.searchDelayed(now).stream().map(this::toDelayed).toList();
	}

	private ActiveTransferView toView(ActiveTransferRow row, Instant now) {
		boolean delayed = DelayDetectionPolicy.isDelayed(row.getStatus(), row.getEstimatedArrivalAt(), now);
		return new ActiveTransferView(row.getTransferExternalId(), row.getTransferNumber(), row.getStatus(),
				new BranchReference(row.getOriginBranchExternalId(), row.getOriginBranchName()),
				new BranchReference(row.getDestinationBranchExternalId(), row.getDestinationBranchName()),
				parsePriority(row.getNotes()), row.getItemCount(), row.getTotalQuantity(), row.getEstimatedArrivalAt(),
				delayed);
	}

	private DeliveryOutcome toOutcome(DeliveryOutcomeRow row) {
		return new DeliveryOutcome(row.getOriginBranchExternalId(), row.getDestinationBranchExternalId(),
				row.getEstimatedArrivalAt(), row.getActualArrivalAt());
	}

	private DelayedTransfer toDelayed(DelayedTransferRow row) {
		return new DelayedTransfer(row.getTransferExternalId(), row.getTransferNumber(),
				row.getOriginBranchExternalId(), row.getDestinationBranchExternalId());
	}

	/**
	 * {@code logistics} declares no dependency on {@code transfers}' {@code TransferNotes}
	 * (boundary rule 3) — this duplicates its F-1 parsing rule for the first line only (design §4,
	 * same reasoning as {@code Transfer.deviationHours()} / {@code DeliveryComplianceCalculator}'s
	 * duplicated arithmetic): two lines cost less than a {@code shared} type existing to carry it.
	 */
	private static String parsePriority(String notes) {
		if (notes != null && notes.startsWith(TOKEN_PREFIX)) {
			String firstLine = notes.split("\n", 2)[0];
			return firstLine.substring(TOKEN_PREFIX.length()).strip();
		}
		return "STANDARD";
	}

	/** Any state, not just active — a manager of a branch later deactivated must still see their own transfers. */
	private Long resolveBranchIdOrSentinel(UUID branchExternalId) {
		return referenceRepository.findBranchIdByExternalId(branchExternalId).orElse(-1L);
	}
}
