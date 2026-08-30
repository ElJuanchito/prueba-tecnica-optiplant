package com.optiplant.inventory.logistics.application.port.out;

import com.optiplant.inventory.shared.alert.OperationalAlertRaised;

/**
 * Secondary port through which {@code logistics}' application layer publishes an alert event
 * without naming {@code ApplicationEventPublisher} (design §5.2, §6.5). {@code AFTER_COMMIT}
 * dispatch mechanics are entirely the adapter's concern, built in a later slice.
 */
public interface LogisticsAlertPublisherPort {

	void publish(OperationalAlertRaised event);
}
