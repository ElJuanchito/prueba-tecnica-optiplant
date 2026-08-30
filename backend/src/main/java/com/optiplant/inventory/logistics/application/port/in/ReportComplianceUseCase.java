package com.optiplant.inventory.logistics.application.port.in;

import com.optiplant.inventory.logistics.domain.model.ComplianceGrouping;
import com.optiplant.inventory.logistics.domain.model.CompliancePage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;

/** On-time compliance reporting (CU-LOG-03, R-26, R-27, design §5.1) — own branch either side, {@code ADMIN} network-wide. */
public interface ReportComplianceUseCase {

	CompliancePage report(AuthenticatedPrincipal actor, ComplianceQuery query);

	record ComplianceQuery(Instant from, Instant to, ComplianceGrouping groupBy, int page, int size) {
	}
}
