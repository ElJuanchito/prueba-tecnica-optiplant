package com.optiplant.inventory.analytics.application.port.out;

import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.RotationDirection;
import com.optiplant.inventory.analytics.domain.service.RotationPageAssembler.RawRotationRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Secondary read port for sales analytics queries (design §4 Q-1, Q-2, Q-3, §8).
 */
public interface SalesAnalyticsPort {

	List<MonthlySales> monthlySales(UUID branchExternalId, Instant from, Instant to);

	List<RawRotationRow> rotation(UUID branchExternalId, Instant from, Instant to,
			RotationDirection direction, int page, int size);

	long rotationCount(UUID branchExternalId, Instant from, Instant to);
}
