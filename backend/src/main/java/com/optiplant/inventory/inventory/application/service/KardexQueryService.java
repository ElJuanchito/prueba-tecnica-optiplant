package com.optiplant.inventory.inventory.application.service;

import com.optiplant.inventory.inventory.application.port.in.QueryKardexUseCase;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort.KardexFilter;
import com.optiplant.inventory.inventory.domain.model.DateRange;
import com.optiplant.inventory.inventory.domain.model.KardexPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only Kardex history (CU-INV-08, R-16). Scoped to the caller's own branch; a corporate
 * {@code ADMIN} reads any branch (contract §5) — a {@code BRANCH_MANAGER} of branch A requesting
 * a movement of branch B simply finds nothing in the filtered result (R-19).
 *
 * <p>{@code @Service} restored in S2 (task 2.14) — see {@code StockMovementService}'s class
 * Javadoc for why S1 shipped this unannotated.
 */
@Service
public class KardexQueryService implements QueryKardexUseCase {

	private final KardexRepositoryPort kardexRepository;

	public KardexQueryService(KardexRepositoryPort kardexRepository) {
		this.kardexRepository = kardexRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public KardexPage list(AuthenticatedPrincipal actor, KardexQuery query) {
		UUID branchScope = actor.isCorporate() ? null : actor.branchId();
		DateRange range = new DateRange(query.from(), query.to());
		return kardexRepository.list(new KardexFilter(branchScope, query.productExternalId(), query.movementType(),
				range, query.page(), query.size()));
	}
}
