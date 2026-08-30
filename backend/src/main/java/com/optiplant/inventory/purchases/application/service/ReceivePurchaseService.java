package com.optiplant.inventory.purchases.application.service;

import com.optiplant.inventory.purchases.application.port.in.ReceivePurchaseUseCase;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort.ProductUnitRef;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderItemNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderNotFoundException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.service.PurchaseAccessPolicy;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionLine;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionLineCommand;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionPlan;
import com.optiplant.inventory.purchases.domain.service.UnitConversionPolicy;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers a goods reception and recalculates the WAC (CU-COM-04, RF-COM-02, RF-COM-04, RN-10),
 * exactly the linear sequence of design §5:
 *
 * <ol>
 *   <li>resolve the receiving branch (corporate {@code ADMIN} &rarr; {@code 403}); reject an
 *       empty or all-zero line set (R-22) <strong>before any lock</strong>;</li>
 *   <li>{@code lockForUpdate} the order row (F-5, T-02);</li>
 *   <li>visibility (R-23/R-25), then the state machine (R-14);</li>
 *   <li>resolve each item against the order's own items; batch-resolve conversion factors (R-09);</li>
 *   <li>{@link PurchaseReceptionPolicy#plan} &rarr; the plan, lines in T-02 lock order;</li>
 *   <li>per plan line, {@code applyMovement} with {@code PURCHASE_RECEIPT}, the effective unit
 *       cost, {@code reference_type = "PURCHASE_ORDER"} and {@code reference_id} = the order's
 *       {@code external_id} (R-15, §2);</li>
 *   <li>{@code save(order.withReception(plan, now))} (R-19);</li>
 *   <li>one {@code audit_logs} entry on the order's branch (T-01, T-03).</li>
 * </ol>
 *
 * <p><strong>Ships without {@code @Service}</strong> while its out-ports have no adapter (S1,
 * design §10 trap 4). S2 task 2.6 restores the stereotype.
 */
@Transactional
public class ReceivePurchaseService implements ReceivePurchaseUseCase {

	private static final String ENTITY_NAME = "PURCHASE_ORDER";
	private static final String REFERENCE_TYPE = "PURCHASE_ORDER";

	private final PurchaseOrderRepositoryPort orderRepository;
	private final PurchaseReferencePort referencePort;
	private final StockMutationPort stockMutationPort;
	private final AuditWritePort auditWritePort;

	public ReceivePurchaseService(PurchaseOrderRepositoryPort orderRepository, PurchaseReferencePort referencePort,
			StockMutationPort stockMutationPort, AuditWritePort auditWritePort) {
		this.orderRepository = orderRepository;
		this.referencePort = referencePort;
		this.stockMutationPort = stockMutationPort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	public PurchaseOrderDetail receive(AuthenticatedPrincipal actor, UUID orderExternalId,
			ReceivePurchaseCommand command) {
		// 1. branch + shape, before any lock
		UUID receivingBranch = PurchaseAccessPolicy.resolveActingBranch(actor);
		List<ReceptionItemCommand> items = command != null ? command.items() : null;
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("a reception must name at least one line");
		}
		if (items.stream().allMatch(ReceivePurchaseService::isZeroOrNull)) {
			throw new IllegalArgumentException("a reception cannot consist only of zero-quantity lines");
		}

		// 2. lock
		PurchaseOrder order = orderRepository.lockForUpdate(orderExternalId)
				.orElseThrow(() -> new PurchaseOrderNotFoundException(orderExternalId));

		// 3. visibility + branch equality (R-23), then state machine (inside withReception)
		PurchaseAccessPolicy.assertVisible(actor, order);
		if (!order.belongsTo(receivingBranch)) {
			throw new PurchaseOrderNotFoundException(orderExternalId);
		}

		// 4. resolve items against the order's own items; batch-resolve conversion factors
		Map<UUID, PurchaseOrderItem> itemsById = order.items().stream()
				.collect(Collectors.toMap(PurchaseOrderItem::externalId, i -> i));
		List<ProductUnitRef> unitRefs = new ArrayList<>();
		for (ReceptionItemCommand line : items) {
			PurchaseOrderItem item = itemsById.get(line.itemExternalId());
			if (item == null) {
				throw new PurchaseOrderItemNotFoundException(line.itemExternalId());
			}
			if (line.unitOfMeasureExternalId() != null) {
				unitRefs.add(new ProductUnitRef(item.productExternalId(), line.unitOfMeasureExternalId()));
			}
		}
		Map<UUID, BigDecimal> factors = unitRefs.isEmpty() ? Map.of() : referencePort.conversionFactors(unitRefs);

		// 5. plan
		List<ReceptionLineCommand> planInput = new ArrayList<>();
		for (ReceptionItemCommand line : items) {
			PurchaseOrderItem item = itemsById.get(line.itemExternalId());
			BigDecimal baseQuantity = toBaseQuantity(item.productExternalId(), line, factors);
			planInput.add(new ReceptionLineCommand(line.itemExternalId(), baseQuantity));
		}
		ReceptionPlan plan = PurchaseReceptionPolicy.plan(order, planInput, actor.role());

		// 6. per plan line, in lock order
		Instant now = Instant.now();
		String notes = command.notes();
		for (ReceptionLine line : plan.lines()) {
			stockMutationPort.applyMovement(new StockMutationCommand(
					order.branchExternalId(),
					line.productExternalId(),
					StockMovementType.PURCHASE_RECEIPT,
					line.receivedQuantity(),
					line.effectiveUnitCost().value(),
					REFERENCE_TYPE,
					order.externalId().toString(),
					notes,
					actor.userId()));
		}

		// 7. accumulate received_quantity + status + received_at
		PurchaseOrder saved = orderRepository.save(order.withReception(plan, now));

		// 8. audit on the order's branch
		auditWritePort.record(new AuditEntryCommand(actor.userId(), order.branchExternalId(),
				"RECEIVE_PURCHASE_ORDER", ENTITY_NAME, order.externalId().toString(), null,
				payloadAfter(plan, actor), null));

		return PurchaseOrderDetailAssembler.toDetail(saved, referencePort);
	}

	private static boolean isZeroOrNull(ReceptionItemCommand line) {
		return line.receivedQuantity() == null || line.receivedQuantity().signum() == 0;
	}

	private static BigDecimal toBaseQuantity(UUID productExternalId, ReceptionItemCommand line,
			Map<UUID, BigDecimal> factors) {
		BigDecimal raw = line.receivedQuantity();
		if (line.unitOfMeasureExternalId() == null || raw == null || raw.signum() <= 0) {
			return raw;
		}
		return UnitConversionPolicy.toBaseUnit(productExternalId, line.unitOfMeasureExternalId(), raw,
				factors.get(productExternalId)).value();
	}

	private static String payloadAfter(ReceptionPlan plan, AuthenticatedPrincipal actor) {
		Map<String, Object> received = new LinkedHashMap<>();
		for (ReceptionLine line : plan.lines()) {
			received.put(line.itemExternalId().toString(), line.receivedQuantity().toPlainString());
		}
		Map<String, Object> excesses = new LinkedHashMap<>();
		plan.excesses().forEach((itemId, excess) -> excesses.put(itemId.toString(), excess.toPlainString()));

		StringBuilder json = new StringBuilder("{\"received\":").append(asJsonObject(received));
		if (!excesses.isEmpty()) {
			json.append(",\"acceptedExcess\":").append(asJsonObject(excesses))
					.append(",\"authorizingRole\":\"").append(actor.role()).append('"');
		}
		return json.append(",\"targetStatus\":\"").append(plan.targetStatus()).append("\"}").toString();
	}

	private static String asJsonObject(Map<String, Object> map) {
		return map.entrySet().stream()
				.map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
				.collect(Collectors.joining(",", "{", "}"));
	}
}
