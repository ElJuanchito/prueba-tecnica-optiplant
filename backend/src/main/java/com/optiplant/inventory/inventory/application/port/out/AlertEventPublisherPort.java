package com.optiplant.inventory.inventory.application.port.out;

import com.optiplant.inventory.shared.alert.OperationalAlertRaised;

/**
 * Secondary port through which {@code inventory}'s application layer publishes an alert event
 * without naming {@code ApplicationEventPublisher} (design §5.2). The {@code AFTER_COMMIT}
 * dispatch mechanics are entirely the adapter's concern, built in a later slice.
 */
public interface AlertEventPublisherPort {

	void publish(OperationalAlertRaised event);
}
