package com.optiplant.inventory.inventory.infrastructure.adapter.in.web;

import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort.NewMovement;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.service.StockMutationPolicy;
import com.optiplant.inventory.inventory.domain.service.StockMutationPolicy.MovementDraft;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-source-only fixture exercising the exact same production ports
 * ({@link BranchInventoryRepositoryPort}, {@link KardexRepositoryPort}) and the real
 * {@link StockMutationPolicy} that {@code StockMovementService} uses, inside one transaction that
 * then either commits or throws — used solely by {@code KardexAtomicityIT} to prove R-18/T-01: the
 * balance update and the Kardex insert commit or fail together. Mirrors
 * {@code AuditAtomicityFixtureService}'s pattern exactly (see its javadoc). Confined to
 * {@code src/test} — it must never reach the packaged production JAR.
 */
@Service
class KardexAtomicityFixtureService {

	private final BranchInventoryRepositoryPort branchInventoryRepository;
	private final KardexRepositoryPort kardexRepository;
	private final PrincipalAccessor principalAccessor;

	KardexAtomicityFixtureService(BranchInventoryRepositoryPort branchInventoryRepository,
			KardexRepositoryPort kardexRepository, PrincipalAccessor principalAccessor) {
		this.branchInventoryRepository = branchInventoryRepository;
		this.kardexRepository = kardexRepository;
		this.principalAccessor = principalAccessor;
	}

	/**
	 * Applies an {@code ADJUSTMENT_POS} of {@code quantity} to {@code productExternalId} in the
	 * caller's own branch — balance update, then Kardex append, exactly as
	 * {@code StockMovementService} does — then throws when {@code shouldFail}, deliberately after
	 * both writes so a rollback proves they are not two independent transactions.
	 */
	@Transactional
	void mutateThenMaybeFail(String referenceId, UUID productExternalId, BigDecimal quantity, boolean shouldFail) {
		AuthenticatedPrincipal principal = principalAccessor.require();
		UUID branchExternalId = principal.branchId();

		BranchInventory current = branchInventoryRepository.lockForUpdate(branchExternalId, productExternalId)
				.orElseGet(() -> branchInventoryRepository.createZeroed(branchExternalId, productExternalId));

		MovementDraft draft = StockMutationPolicy.apply(current, StockMovementType.ADJUSTMENT_POS,
				new Quantity(quantity), current.averageCost(), "kardex-atomicity-fixture", referenceId, null,
				principal.userId(), Instant.now());

		branchInventoryRepository.save(draft.updated());
		kardexRepository.append(toNewMovement(draft.movement()));

		if (shouldFail) {
			throw new AtomicityFixtureFailure("Deliberate failure after balance update and Kardex append");
		}
	}

	private static NewMovement toNewMovement(KardexMovement.Draft draft) {
		return new NewMovement(draft.branchExternalId(), draft.productExternalId(), draft.movementType(),
				draft.quantity(), draft.unitCost(), draft.totalCost(), draft.previousStock(), draft.resultingStock(),
				draft.referenceType(), draft.referenceId(), draft.notes(), draft.userExternalId());
	}

	/** Deliberately unchecked and unmapped by {@link InventoryExceptionHandler} — the test only
	 *  cares that the request does not succeed, not about a specific status. */
	static class AtomicityFixtureFailure extends RuntimeException {
		AtomicityFixtureFailure(String message) {
			super(message);
		}
	}
}
