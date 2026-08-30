package com.optiplant.inventory.logistics.application.port.in;

import com.optiplant.inventory.logistics.domain.model.ActiveTransferPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;

/** Active-transfer monitoring (CU-LOG-02, R-25, design §5.1) — own branch either side, {@code ADMIN} network-wide. */
public interface MonitorTransfersUseCase {

	ActiveTransferPage listActive(AuthenticatedPrincipal actor, ActiveTransferQuery query);

	record ActiveTransferQuery(String status, Boolean delayed, int page, int size) {
	}
}
