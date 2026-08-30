package com.optiplant.inventory.transfers.infrastructure.adapter.out.event;

import com.optiplant.inventory.transfers.application.port.out.TransferAlertPublisherPort;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ApplicationEventPublisher} so the application layer never names a Spring type
 * (design §5.2) — exactly {@code inventory}'s {@code SpringAlertEventPublisher}. The
 * {@code AFTER_COMMIT} dispatch mechanics live entirely in {@code notifications}'
 * {@code OperationalAlertListener} (P-09): no change needed there.
 */
@Component
public class SpringTransferAlertPublisher implements TransferAlertPublisherPort {

	private final ApplicationEventPublisher applicationEventPublisher;

	public SpringTransferAlertPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void publish(OperationalAlertRaised event) {
		applicationEventPublisher.publishEvent(event);
	}
}
