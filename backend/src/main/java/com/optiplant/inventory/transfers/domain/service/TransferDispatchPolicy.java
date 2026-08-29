package com.optiplant.inventory.transfers.domain.service;

import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.model.DispatchLine;
import com.optiplant.inventory.transfers.domain.model.SettledQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per line: dispatched {@code > 0} and {@code <= requested} — the post-approval agreed quantity
 * (R-13, design §3.3); an item not named is refused, since sending less is a quantity reduction,
 * not an item omission.
 *
 * <p>{@link #apply} is what {@code Transfer.dispatch} calls to validate and build the post-dispatch
 * item state. {@link #plan} is a second, independent entry point the application service calls to
 * obtain the §7.1 deterministic lock order for {@code StockMutationPort} calls — the transfer row
 * is always locked first (F-5), then {@code branch_inventories} rows sorted ascending by branch
 * UUID then product UUID, so two concurrent transfers between the same pair cannot deadlock.
 */
public final class TransferDispatchPolicy {

	private TransferDispatchPolicy() {
	}

	public static DispatchApplication apply(List<TransferItem> items, List<DispatchLine> lines) {
		Map<UUID, BigDecimal> validated = validate(items, lines);
		List<TransferItem> updated = new ArrayList<>();
		List<String> observations = new ArrayList<>();
		for (TransferItem item : items) {
			BigDecimal dispatchedQuantity = validated.get(item.externalId());
			BigDecimal agreed = item.requestedQuantity().value();
			if (dispatchedQuantity.compareTo(agreed) < 0) {
				observations.add("Item " + item.productExternalId() + " dispatched at " + dispatchedQuantity
						+ " instead of the agreed " + agreed);
			}
			updated.add(new TransferItem(item.externalId(), item.productExternalId(), item.requestedQuantity(),
					new SettledQuantity(dispatchedQuantity), item.receivedQuantity(), item.discrepancyQuantity(),
					item.discrepancyReason()));
		}
		return new DispatchApplication(updated, observations);
	}

	public static DispatchPlan plan(UUID originBranchExternalId, UUID destinationBranchExternalId,
			List<TransferItem> items, List<DispatchLine> lines) {
		Map<UUID, BigDecimal> validated = validate(items, lines);
		List<DispatchPlanLine> planLines = new ArrayList<>();
		for (TransferItem item : items) {
			SettledQuantity quantity = new SettledQuantity(validated.get(item.externalId()));
			planLines.add(new DispatchPlanLine(originBranchExternalId, item.productExternalId(), item.externalId(),
					quantity, DispatchOperation.STOCK_OUT));
			planLines.add(new DispatchPlanLine(destinationBranchExternalId, item.productExternalId(),
					item.externalId(), quantity, DispatchOperation.IN_TRANSIT_INCREMENT));
		}
		planLines.sort(Comparator.comparing(DispatchPlanLine::branchExternalId)
				.thenComparing(DispatchPlanLine::productExternalId));
		return new DispatchPlan(planLines);
	}

	private static Map<UUID, BigDecimal> validate(List<TransferItem> items, List<DispatchLine> lines) {
		if (lines == null || lines.size() != items.size()) {
			throw new InvalidTransferQuantityException("every item must be named exactly once at dispatch");
		}
		Map<UUID, BigDecimal> byItem = new HashMap<>();
		for (DispatchLine line : lines) {
			if (byItem.put(line.itemExternalId(), line.dispatchedQuantity()) != null) {
				throw new DuplicateTransferItemException();
			}
		}
		for (TransferItem item : items) {
			BigDecimal dispatchedQuantity = byItem.get(item.externalId());
			if (dispatchedQuantity == null) {
				throw new TransferItemNotFoundException(item.externalId());
			}
			BigDecimal agreed = item.requestedQuantity().value();
			if (dispatchedQuantity.signum() <= 0 || dispatchedQuantity.compareTo(agreed) > 0) {
				throw new InvalidTransferQuantityException(
						"dispatched quantity must be > 0 and <= the agreed quantity of " + agreed);
			}
		}
		return byItem;
	}

	/** The post-dispatch item state and the observation lines documenting any shortfall (CU-TRA-03 FA-01). */
	public record DispatchApplication(List<TransferItem> items, List<String> observations) {
	}

	/** The §7.1 lock-ordered pairs the application service replays against {@code StockMutationPort}. */
	public record DispatchPlan(List<DispatchPlanLine> lines) {
	}

	/** Whether a {@link DispatchPlanLine} calls {@code applyMovement} (origin) or {@code shiftInTransit} (destination). */
	public enum DispatchOperation {
		STOCK_OUT, IN_TRANSIT_INCREMENT
	}

	public record DispatchPlanLine(UUID branchExternalId, UUID productExternalId, UUID itemExternalId,
			SettledQuantity quantity, DispatchOperation operation) {
	}
}
