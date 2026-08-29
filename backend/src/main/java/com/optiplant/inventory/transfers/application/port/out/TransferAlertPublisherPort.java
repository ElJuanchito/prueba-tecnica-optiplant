package com.optiplant.inventory.transfers.application.port.out;

import com.optiplant.inventory.shared.alert.OperationalAlertRaised;

/**
 * Secondary port through which {@code transfers}' application layer publishes an alert event
 * without naming {@code ApplicationEventPublisher} (design §5.2). {@code AFTER_COMMIT} dispatch
 * mechanics are entirely the adapter's concern, built in a later slice.
 */
public interface TransferAlertPublisherPort {

	void publish(OperationalAlertRaised event);
}
