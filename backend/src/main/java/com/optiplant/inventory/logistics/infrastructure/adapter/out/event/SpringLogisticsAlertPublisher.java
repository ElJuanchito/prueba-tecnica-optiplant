package com.optiplant.inventory.logistics.infrastructure.adapter.out.event;

import com.optiplant.inventory.logistics.application.port.out.LogisticsAlertPublisherPort;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ApplicationEventPublisher} so the application layer never names a Spring type
 * (design §5.2, §6.5) — exactly {@code inventory}'s {@code SpringAlertEventPublisher} and
 * {@code transfers}' {@code SpringTransferAlertPublisher}. {@code AFTER_COMMIT} dispatch mechanics
 * live entirely in {@code notifications}' {@code OperationalAlertListener} (P-09).
 */
@Component
public class SpringLogisticsAlertPublisher implements LogisticsAlertPublisherPort {

	private final ApplicationEventPublisher applicationEventPublisher;

	public SpringLogisticsAlertPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void publish(OperationalAlertRaised event) {
		applicationEventPublisher.publishEvent(event);
	}
}
