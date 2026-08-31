package com.optiplant.inventory.analytics.application.port.in;

import com.optiplant.inventory.shared.availability.NetworkAvailabilityView;
import java.util.UUID;

/**
 * Primary use case for external network availability query (CU-EXT-01, RF-EXT-01).
 */
public interface QueryNetworkAvailabilityUseCase {

	NetworkAvailabilityView availability(UUID productExternalId);
}
