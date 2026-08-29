package com.optiplant.inventory.inventory.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optiplant.inventory.inventory.application.port.in.RegisterStockMovementUseCase;
import com.optiplant.inventory.inventory.application.port.out.AlertEventPublisherPort;
import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort.NewMovement;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.MovementReason;
import com.optiplant.inventory.inventory.domain.model.MovementReceipt;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import com.optiplant.inventory.inventory.domain.service.AdjustmentPolicy;
import com.optiplant.inventory.inventory.domain.service.AdjustmentPolicy.AdjustmentDecision;
import com.optiplant.inventory.inventory.domain.service.AlertRaisingPolicy;
import com.optiplant.inventory.inventory.domain.service.BranchScopePolicy;
import com.optiplant.inventory.inventory.domain.service.StockMutationPolicy;
import com.optiplant.inventory.inventory.domain.service.StockMutationPolicy.MovementDraft;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates manual stock movements (CU-INV-05, CU-INV-06): lock the branch row, apply the
 * domain policy, save, append the Kardex row, audit, then publish the breach alert — publish is
 * the transaction's last statement, still inside it (design §11 trap 4, T-04): the
 * {@code @TransactionalEventListener} only fires when the publish happened inside an active
 * transaction.
 *
 * <p>Audit {@code branchId} is the branch of the mutated resource (T-03), matching every mutation
 * with an entry that lives or dies with the balance and Kardex writes in the same transaction
 * (RN-02, RNF-INT-01, CLAUDE.md's synchronous-effects invariant).
 *
 * <p>Deliberately <strong>not</strong> {@code @Service} yet: every constructor dependency here is
 * a port with no adapter in this slice (S1 is domain + application only). Registering this as a
 * bean now would fail {@code ApplicationContextIT}'s full context boot with an unsatisfied
 * dependency. The {@code @Service} annotation is added in S2, alongside the adapters that satisfy
 * these ports.
 */
public class StockMovementService implements RegisterStockMovementUseCase {

	private final BranchInventoryRepositoryPort branchInventoryRepository;
	private final KardexRepositoryPort kardexRepository;
	private final AuditWritePort auditWritePort;
	private final AlertEventPublisherPort alertEventPublisherPort;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public StockMovementService(BranchInventoryRepositoryPort branchInventoryRepository,
			KardexRepositoryPort kardexRepository, AuditWritePort auditWritePort,
			AlertEventPublisherPort alertEventPublisherPort) {
		this.branchInventoryRepository = branchInventoryRepository;
		this.kardexRepository = kardexRepository;
		this.auditWritePort = auditWritePort;
		this.alertEventPublisherPort = alertEventPublisherPort;
	}

	@Override
	@Transactional
	public MovementReceipt adjust(AuthenticatedPrincipal actor, AdjustStockCommand command) {
		MovementReason reason = new MovementReason(command.reason());
		UUID branchExternalId = BranchScopePolicy.resolveOwnBranch(actor);

		BranchInventory current = lockOrCreate(branchExternalId, command.productExternalId());
		AdjustmentDecision decision = AdjustmentPolicy.decide(current.currentStock().value(),
				command.countedQuantity());

		// CU-INV-05's command carries no unit cost, yet P-03 requires one for the inbound
		// ADJUSTMENT_POS case. RN-10's revaluation is out of scope regardless of direction, so the
		// branch's own current average cost is the only cost this operation can supply — this
		// values a positive count adjustment exactly as an outbound one already is (R-12's logic
		// generalized to both signs, not a client-supplied figure).
		UnitCost suppliedCost = decision.movementType().requiresSuppliedCost() ? current.averageCost() : null;

		return mutate(current, decision.movementType(), decision.quantity(), suppliedCost, null, null, reason.value(),
				actor.userId(), "ADJUST_STOCK");
	}

	@Override
	@Transactional
	public MovementReceipt writeOff(AuthenticatedPrincipal actor, WriteOffCommand command) {
		MovementReason reason = new MovementReason(command.reason());
		UUID branchExternalId = BranchScopePolicy.resolveOwnBranch(actor);
		Quantity quantity = new Quantity(command.quantity());

		BranchInventory current = lockOrCreate(branchExternalId, command.productExternalId());

		return mutate(current, StockMovementType.DAMAGE_WASTE, quantity, null, null, null, reason.value(),
				actor.userId(), "WRITE_OFF_STOCK");
	}

	private BranchInventory lockOrCreate(UUID branchExternalId, UUID productExternalId) {
		return branchInventoryRepository.lockForUpdate(branchExternalId, productExternalId)
				.orElseGet(() -> branchInventoryRepository.createZeroed(branchExternalId, productExternalId));
	}

	private MovementReceipt mutate(BranchInventory current, StockMovementType movementType, Quantity quantity,
			UnitCost suppliedCost, String referenceType, String referenceId, String notes,
			UUID actorUserExternalId, String auditAction) {
		Instant now = Instant.now();
		MovementDraft draft = StockMutationPolicy.apply(current, movementType, quantity, suppliedCost, referenceType,
				referenceId, notes, actorUserExternalId, now);

		BranchInventory saved = branchInventoryRepository.save(draft.updated());
		KardexMovement movement = kardexRepository.append(toNewMovement(draft.movement()));

		auditWritePort.record(new AuditEntryCommand(actorUserExternalId, current.branchExternalId(), auditAction,
				"kardex_movements", movement.externalId().toString(), null, serializeMovement(movement), null));

		AlertRaisingPolicy.evaluate(saved, movement.externalId())
				.ifPresent(breach -> alertEventPublisherPort.publish(AlertRaisingPolicy.render(breach)));

		return new MovementReceipt(movement.externalId(), movement.movementType(), movement.quantity().value(),
				movement.previousStock(), movement.resultingStock(), movement.createdAt());
	}

	private static NewMovement toNewMovement(KardexMovement.Draft draft) {
		return new NewMovement(draft.branchExternalId(), draft.productExternalId(), draft.movementType(),
				draft.quantity(), draft.unitCost(), draft.totalCost(), draft.previousStock(), draft.resultingStock(),
				draft.referenceType(), draft.referenceId(), draft.notes(), draft.userExternalId());
	}

	private String serializeMovement(KardexMovement movement) {
		try {
			return OBJECT_MAPPER.writeValueAsString(new MovementAuditPayload(movement.movementType().name(),
					movement.quantity().value(), movement.unitCost().value(), movement.totalCost(),
					movement.previousStock(), movement.resultingStock()));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record MovementAuditPayload(String movementType, BigDecimal quantity, BigDecimal unitCost,
			BigDecimal totalCost, BigDecimal previousStock, BigDecimal resultingStock) {
	}
}
