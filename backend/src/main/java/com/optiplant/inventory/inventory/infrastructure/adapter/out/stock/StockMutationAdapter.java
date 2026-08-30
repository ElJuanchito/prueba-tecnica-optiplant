package com.optiplant.inventory.inventory.infrastructure.adapter.out.stock;

import com.optiplant.inventory.inventory.application.port.out.AlertEventPublisherPort;
import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort.NewMovement;
import com.optiplant.inventory.inventory.domain.exception.InsufficientStockException;
import com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.inventory.domain.exception.UnitCostContractViolationException;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.StockLevel;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import com.optiplant.inventory.inventory.domain.service.AlertRaisingPolicy;
import com.optiplant.inventory.inventory.domain.service.StockMutationPolicy;
import com.optiplant.inventory.inventory.domain.service.StockMutationPolicy.MovementDraft;
import com.optiplant.inventory.shared.stock.InTransitDirection;
import com.optiplant.inventory.shared.stock.InTransitShiftCommand;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import com.optiplant.inventory.shared.stock.StockMutationRejectedException;
import com.optiplant.inventory.shared.stock.StockMutationRejectedException.Reason;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single {@link StockMutationPort} implementation (contract §2.2, D-4, design §2.3) — the
 * first real adapter behind the port {@code add-inventory-module} only declared. Reuses
 * {@link BranchInventoryRepositoryPort}, {@link KardexRepositoryPort} and
 * {@link StockMutationPolicy} exactly as {@code StockMovementService} does for a manual
 * adjustment, joining the caller's own transaction (P-01, {@code Propagation.REQUIRED} — no
 * {@code @Transactional} annotation here on purpose, so this method always runs inside whatever
 * transaction {@code transfers} (or any future caller) already opened).
 *
 * <p>Translates {@code inventory}'s own domain exceptions into {@link StockMutationRejectedException}
 * on the way out (D-4): a type {@code transfers} cannot catch directly, since boundary rule 3
 * forbids importing another module's package. {@code inventory}'s own use cases and
 * {@code InventoryExceptionHandler} are untouched — this adapter is the only translation point.
 */
@Component
public class StockMutationAdapter implements StockMutationPort {

	private final BranchInventoryRepositoryPort branchInventoryRepository;
	private final KardexRepositoryPort kardexRepository;
	private final AlertEventPublisherPort alertEventPublisherPort;

	public StockMutationAdapter(BranchInventoryRepositoryPort branchInventoryRepository,
			KardexRepositoryPort kardexRepository, AlertEventPublisherPort alertEventPublisherPort) {
		this.branchInventoryRepository = branchInventoryRepository;
		this.kardexRepository = kardexRepository;
		this.alertEventPublisherPort = alertEventPublisherPort;
	}

	@Override
	public UUID applyMovement(StockMutationCommand command) {
		try {
			BranchInventory current = lockOrCreate(command.branchExternalId(), command.productExternalId());
			UnitCost suppliedCost = command.unitCost() == null ? null : new UnitCost(command.unitCost());
			MovementDraft draft = StockMutationPolicy.apply(current, command.movementType(),
					new Quantity(command.quantity()), suppliedCost, command.referenceType(), command.referenceId(),
					command.notes(), command.actorUserExternalId(), Instant.now());

			BranchInventory saved = branchInventoryRepository.save(draft.updated());
			KardexMovement movement = kardexRepository.append(toNewMovement(draft.movement()));
			AlertRaisingPolicy.evaluate(saved, movement.externalId())
					.ifPresent(b -> alertEventPublisherPort.publish(AlertRaisingPolicy.render(b)));
			return movement.externalId();
		} catch (InsufficientStockException ex) {
			throw new StockMutationRejectedException(Reason.INSUFFICIENT_STOCK, ex.getMessage());
		} catch (UnitCostContractViolationException ex) {
			throw new StockMutationRejectedException(Reason.UNIT_COST_CONTRACT, ex.getMessage());
		} catch (ProductNotFoundException ex) {
			throw StockMutationRejectedException.unknownProduct(command.productExternalId());
		} catch (IllegalStateException ex) {
			// requireBranchId's own failure mode inside BranchInventoryPersistenceAdapter (design
			// §6.1) — unknown branch, the counterpart of ProductNotFoundException above.
			throw StockMutationRejectedException.unknownBranch(command.branchExternalId());
		}
	}

	@Override
	public void shiftInTransit(InTransitShiftCommand command) {
		try {
			BranchInventory current = lockOrCreate(command.branchExternalId(), command.productExternalId());
			BigDecimal delta = command.quantity();
			BigDecimal previous = current.inTransitStock().value();
			BigDecimal updatedValue = command.direction() == InTransitDirection.INCREMENT ? previous.add(delta)
					: previous.subtract(delta);

			BranchInventory updated = new BranchInventory(current.externalId(), current.branchExternalId(),
					current.productExternalId(), current.currentStock(), current.reservedStock(),
					new StockLevel(updatedValue), current.minStockThreshold(), current.averageCost(), Instant.now());
			branchInventoryRepository.save(updated);
		} catch (ProductNotFoundException ex) {
			throw StockMutationRejectedException.unknownProduct(command.productExternalId());
		} catch (IllegalStateException ex) {
			throw StockMutationRejectedException.unknownBranch(command.branchExternalId());
		}
	}

	private BranchInventory lockOrCreate(UUID branchExternalId, UUID productExternalId) {
		return branchInventoryRepository.lockForUpdate(branchExternalId, productExternalId)
				.orElseGet(() -> branchInventoryRepository.createZeroed(branchExternalId, productExternalId));
	}

	private static NewMovement toNewMovement(KardexMovement.Draft draft) {
		return new NewMovement(draft.branchExternalId(), draft.productExternalId(), draft.movementType(),
				draft.quantity(), draft.unitCost(), draft.totalCost(), draft.previousStock(), draft.resultingStock(),
				draft.referenceType(), draft.referenceId(), draft.notes(), draft.userExternalId());
	}
}
