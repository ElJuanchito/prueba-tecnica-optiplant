package com.optiplant.inventory.logistics.application.port.in;

/**
 * The scheduled delay detector (R-28, CU-ALE-01, design §5.1, §6.5). Invoked by
 * {@code TransferDelayScheduler} (S2), not by a request — the only port in this module with no
 * {@code AuthenticatedPrincipal} parameter.
 */
public interface DetectTransferDelaysUseCase {

	void detect();
}
