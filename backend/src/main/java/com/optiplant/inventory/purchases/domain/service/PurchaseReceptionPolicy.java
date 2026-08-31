package com.optiplant.inventory.purchases.domain.service;

import com.optiplant.inventory.purchases.domain.exception.InvalidOrderQuantityException;
import com.optiplant.inventory.purchases.domain.exception.InvalidUnitCostException;
import com.optiplant.inventory.purchases.domain.exception.OverReceiptNotAuthorizedException;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderItemNotFoundException;
import com.optiplant.inventory.purchases.domain.model.Money;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The reception's whole rule set (R-14, R-16, R-17, R-19, R-22, design §3.4).
 *
 * <ul>
 *   <li>Refuses a negative quantity ({@link InvalidOrderQuantityException}) and an empty or
 *       all-zero line set ({@link IllegalArgumentException} &rarr; {@code 400 invalid_request},
 *       R-22 — {@code kardex_movements} has {@code CHECK (quantity > 0)}, refused before the write
 *       per T-07); drops {@code receivedQuantity == 0} lines from the plan.</li>
 *   <li>Per line {@code effectiveUnitCost = item.effectiveUnitCost()}; an absent cost is
 *       {@link InvalidUnitCostException}, never a default of zero (R-17, CU-COM-04 EX-02).</li>
 *   <li>{@code excess = max(0, received - item.pendingQuantity())}; any {@code excess > 0} with
 *       {@code actorRole == OPERATOR} &rarr; {@link OverReceiptNotAuthorizedException}
 *       &rarr; {@code 403 over_receipt_requires_manager} (R-16, PA-02).</li>
 *   <li>{@code targetStatus = RECEIVED} when <strong>every</strong> line's accumulated
 *       {@code received >= ordered} after the plan, {@code PARTIALLY_RECEIVED} otherwise (R-19);
 *       lines the request does not name keep their stored {@code received_quantity}.</li>
 * </ul>
 *
 * <p>{@link ReceptionPlan#lines()} are sorted ascending by product {@code external_id} (T-02).
 */
public final class PurchaseReceptionPolicy {

	private PurchaseReceptionPolicy() {
	}

	/** A client-supplied reception line — an {@code (itemExternalId, receivedQuantity)} pair (D-7). */
	public record ReceptionLineCommand(UUID itemExternalId, BigDecimal receivedQuantity) {
	}

	/** One planned Kardex-bound movement, addressed by product for the T-02 lock order. */
	public record ReceptionLine(UUID itemExternalId, UUID productExternalId, BigDecimal receivedQuantity,
			Money effectiveUnitCost) {
	}

	/**
	 * The computed reception: the non-zero lines in lock order, the resolved target status, and
	 * the accepted excess per item ({@code itemExternalId -> excess}) for the audit payload (R-16).
	 */
	public record ReceptionPlan(List<ReceptionLine> lines, PurchaseOrderStatus targetStatus,
			Map<UUID, BigDecimal> excesses) {
	}

	public static ReceptionPlan plan(PurchaseOrder order, List<ReceptionLineCommand> commands, Role actorRole) {
		if (commands == null || commands.isEmpty()) {
			throw new IllegalArgumentException("a reception must name at least one line");
		}

		Map<UUID, BigDecimal> requestedByItem = new LinkedHashMap<>();
		for (ReceptionLineCommand command : commands) {
			if (command.itemExternalId() == null) {
				throw new IllegalArgumentException("a reception line must name an item");
			}
			BigDecimal received = command.receivedQuantity();
			if (received == null || received.signum() < 0) {
				throw new InvalidOrderQuantityException("received quantity must be zero or positive");
			}
			if (requestedByItem.put(command.itemExternalId(), received) != null) {
				throw new IllegalArgumentException("item " + command.itemExternalId() + " named twice in one reception");
			}
		}

		boolean allZero = requestedByItem.values().stream().allMatch(q -> q.signum() == 0);
		if (allZero) {
			throw new IllegalArgumentException("a reception cannot consist only of zero-quantity lines");
		}

		Map<UUID, PurchaseOrderItem> itemsById = new LinkedHashMap<>();
		for (PurchaseOrderItem item : order.items()) {
			itemsById.put(item.externalId(), item);
		}
		for (UUID itemExternalId : requestedByItem.keySet()) {
			if (!itemsById.containsKey(itemExternalId)) {
				throw new PurchaseOrderItemNotFoundException(itemExternalId);
			}
		}

		Map<UUID, BigDecimal> excesses = new LinkedHashMap<>();
		List<ReceptionLine> lines = new ArrayList<>();
		boolean allComplete = true;

		for (PurchaseOrderItem item : order.items()) {
			BigDecimal requested = requestedByItem.getOrDefault(item.externalId(), BigDecimal.ZERO);
			BigDecimal accumulated = item.receivedQuantity().add(requested);
			if (accumulated.compareTo(item.orderedQuantity().value()) < 0) {
				allComplete = false;
			}
			if (requested.signum() <= 0) {
				continue;
			}

			Money effectiveUnitCost = item.effectiveUnitCost();
			if (effectiveUnitCost == null) {
				throw new InvalidUnitCostException("purchase order line " + item.externalId() + " has no usable unit cost");
			}

			BigDecimal excess = requested.subtract(item.pendingQuantity()).max(BigDecimal.ZERO);
			if (excess.signum() > 0) {
				if (actorRole == Role.OPERATOR) {
					throw new OverReceiptNotAuthorizedException(
							"receiving " + requested + " above the pending balance of " + item.pendingQuantity()
									+ " requires a branch manager");
				}
				excesses.put(item.externalId(), excess);
			}

			lines.add(new ReceptionLine(item.externalId(), item.productExternalId(), requested, effectiveUnitCost));
		}

		lines.sort(Comparator.comparing(ReceptionLine::productExternalId));
		PurchaseOrderStatus targetStatus = allComplete ? PurchaseOrderStatus.RECEIVED
				: PurchaseOrderStatus.PARTIALLY_RECEIVED;
		return new ReceptionPlan(List.copyOf(lines), targetStatus, Map.copyOf(excesses));
	}
}
