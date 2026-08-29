package com.optiplant.inventory.inventory.infrastructure.adapter.out.event;

import com.optiplant.inventory.inventory.application.port.out.AlertEventPublisherPort;
import com.optiplant.inventory.shared.alert.OperationalAlertRaised;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ApplicationEventPublisher} so the application layer never names a Spring type
 * (design §5.2). The {@code AFTER_COMMIT} dispatch mechanics live entirely in
 * {@code notifications}'s {@code OperationalAlertListener}, which subscribes to
 * {@link OperationalAlertRaised} through {@code @TransactionalEventListener}.
 */
@Component
public class SpringAlertEventPublisher implements AlertEventPublisherPort {

	private final ApplicationEventPublisher applicationEventPublisher;

	public SpringAlertEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void publish(OperationalAlertRaised event) {
		applicationEventPublisher.publishEvent(event);
	}
}
