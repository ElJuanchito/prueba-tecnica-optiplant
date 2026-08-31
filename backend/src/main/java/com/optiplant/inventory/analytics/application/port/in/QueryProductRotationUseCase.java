package com.optiplant.inventory.analytics.application.port.in;

import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.RotationDirection;
import com.optiplant.inventory.analytics.domain.model.RotationLine;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;

/**
 * Primary use case for rotation and ABC / Pareto analysis (CU-DSH-01, RF-DSH-02).
 */
public interface QueryProductRotationUseCase {

	AnalyticsPage<RotationLine> rotation(AuthenticatedPrincipal actor, RotationQuery query);

	record RotationQuery(Instant from, Instant to, RotationDirection direction,
			UUID branchExternalId, int page, int size) {
	}
}
