package com.optiplant.inventory.inventory.application.port.in;

import com.optiplant.inventory.inventory.domain.model.KardexPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read a product's Kardex history (CU-INV-08, design §5.1). Scoped to the caller's own branch; a
 * corporate {@code ADMIN} reads any branch (contract §5, R-19).
 */
public interface QueryKardexUseCase {

	KardexPage list(AuthenticatedPrincipal actor, KardexQuery query);

	record KardexQuery(UUID productExternalId, StockMovementType movementType, Instant from, Instant to, int page,
			int size) {
	}
}
