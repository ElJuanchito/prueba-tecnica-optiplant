package com.optiplant.inventory.logistics.application.service;

import com.optiplant.inventory.logistics.application.port.in.MonitorTransfersUseCase;
import com.optiplant.inventory.logistics.application.port.out.TransferMonitorReadPort;
import com.optiplant.inventory.logistics.application.port.out.TransferMonitorReadPort.ActiveTransferFilter;
import com.optiplant.inventory.logistics.domain.model.ActiveTransferPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active-transfer monitoring (CU-LOG-02, R-25): own branch either side, {@code ADMIN}
 * network-wide (RN-08). {@code logistics} never writes {@code transfers} rows — this is a pure
 * read through {@link TransferMonitorReadPort} (P-12).
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc ({@code transfers} module).
 */
public class MonitorTransfersService implements MonitorTransfersUseCase {

	private final TransferMonitorReadPort monitorReadPort;

	public MonitorTransfersService(TransferMonitorReadPort monitorReadPort) {
		this.monitorReadPort = monitorReadPort;
	}

	@Override
	@Transactional(readOnly = true)
	public ActiveTransferPage listActive(AuthenticatedPrincipal actor, ActiveTransferQuery query) {
		UUID callerBranchExternalId = actor.role() == Role.ADMIN ? null : actor.branchId();
		return monitorReadPort.listActive(new ActiveTransferFilter(callerBranchExternalId, query.status(),
				query.delayed(), query.page(), query.size()));
	}
}
