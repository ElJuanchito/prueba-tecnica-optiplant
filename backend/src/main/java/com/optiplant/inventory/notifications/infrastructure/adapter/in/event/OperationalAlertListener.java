package com.optiplant.inventory.notifications.infrastructure.adapter.in.event;

import com.optiplant.inventory.notifications.application.port.in.ManageAlertsUseCase;
import com.optiplant.inventory.notifications.application.port.in.ManageAlertsUseCase.RaiseAlertCommand;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to {@link OperationalAlertRaised} {@code AFTER_COMMIT}, in its own
 * {@code REQUIRES_NEW} transaction (design §6.3, P-10, T-04). {@code inventory}'s
 * {@code SpringAlertEventPublisher} publishes this event as the last statement of an
 * {@code @Transactional} service method — {@link TransactionalEventListener} only fires when
 * the publish happened inside an active transaction (design §11 trap 4).
 *
 * <p>The whole body is wrapped in a {@code try/catch (RuntimeException)} that logs and returns:
 * nothing this listener can throw may reach the mutation's caller, whose transaction has
 * already committed by the time this method runs (P-10, RNF-OBS-01).
 */
@Component
public class OperationalAlertListener {

	private static final Logger LOG = LoggerFactory.getLogger(OperationalAlertListener.class);

	private final ManageAlertsUseCase manageAlertsUseCase;

	public OperationalAlertListener(ManageAlertsUseCase manageAlertsUseCase) {
		this.manageAlertsUseCase = manageAlertsUseCase;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onOperationalAlertRaised(OperationalAlertRaised event) {
		try {
			manageAlertsUseCase.raise(new RaiseAlertCommand(event.branchExternalId(), event.alertType(),
					event.severity(), event.subjectToken(), event.message()));
		} catch (RuntimeException ex) {
			LOG.error("Failed to raise alert for branch={} type={} subject={}: {}", event.branchExternalId(),
					event.alertType(), event.subjectToken(), ex.getMessage(), ex);
		}
	}
}
