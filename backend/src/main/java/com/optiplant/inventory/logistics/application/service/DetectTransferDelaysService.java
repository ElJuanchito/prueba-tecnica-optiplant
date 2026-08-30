package com.optiplant.inventory.logistics.application.service;

import com.optiplant.inventory.logistics.application.port.in.DetectTransferDelaysUseCase;
import com.optiplant.inventory.logistics.application.port.out.LogisticsAlertPublisherPort;
import com.optiplant.inventory.logistics.application.port.out.TransferMonitorReadPort;
import com.optiplant.inventory.logistics.domain.model.DelayedTransfer;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scheduled delay detector (R-28, CU-ALE-01, design §6.5, D-5). {@code readOnly = true}
 * because it mutates no transfer state and takes no lock — R-28's requirement — while still
 * running inside an active transaction, so {@code @TransactionalEventListener(AFTER_COMMIT)}
 * fires. One {@code LOGISTIC_DELAY} event per involved branch, keyed on the transfer
 * {@code external_id} so {@code notifications} deduplicates it while the condition persists.
 *
 * <p>{@code @Service} and the {@code TransferDelayScheduler} wiring arrive in S2 (task 2.6) — see
 * {@code RequestTransferService}'s class Javadoc ({@code transfers} module) for why S1 ships this
 * unannotated.
 */
@Service
public class DetectTransferDelaysService implements DetectTransferDelaysUseCase {

	private final TransferMonitorReadPort monitorReadPort;
	private final LogisticsAlertPublisherPort alertPublisherPort;

	public DetectTransferDelaysService(TransferMonitorReadPort monitorReadPort,
			LogisticsAlertPublisherPort alertPublisherPort) {
		this.monitorReadPort = monitorReadPort;
		this.alertPublisherPort = alertPublisherPort;
	}

	@Override
	@Transactional(readOnly = true)
	public void detect() {
		Instant now = Instant.now();
		List<DelayedTransfer> delayed = monitorReadPort.listDelayed(now);
		for (DelayedTransfer transfer : delayed) {
			publishFor(transfer, now);
		}
	}

	private void publishFor(DelayedTransfer transfer, Instant now) {
		String message = "Transfer " + transfer.transferNumber() + " is past its estimated arrival";
		alertPublisherPort.publish(new OperationalAlertRaised(transfer.originBranchExternalId(),
				AlertType.LOGISTIC_DELAY, AlertSeverity.WARNING, transfer.transferExternalId().toString(), message,
				now));
		alertPublisherPort.publish(new OperationalAlertRaised(transfer.destinationBranchExternalId(),
				AlertType.LOGISTIC_DELAY, AlertSeverity.WARNING, transfer.transferExternalId().toString(), message,
				now));
	}
}
