package com.optiplant.inventory.analytics.application.service;

import com.optiplant.inventory.analytics.application.port.in.QueryNetworkAvailabilityUseCase;
import com.optiplant.inventory.analytics.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.shared.availability.NetworkAvailabilityPort;
import com.optiplant.inventory.shared.availability.NetworkAvailabilityView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates network availability queries for external consumers (CU-EXT-01, RF-EXT-01, design §2, §8).
 * Injects {@link NetworkAvailabilityPort} from {@code shared} directly.
 *
 * <p>{@code @Service} restored in S2 (design §12 trap 6).
 */
@Service
public class QueryNetworkAvailabilityService implements QueryNetworkAvailabilityUseCase {

	private final NetworkAvailabilityPort networkAvailabilityPort;

	public QueryNetworkAvailabilityService(NetworkAvailabilityPort networkAvailabilityPort) {
		this.networkAvailabilityPort = networkAvailabilityPort;
	}

	@Override
	@Transactional(readOnly = true)
	public NetworkAvailabilityView availability(UUID productExternalId) {
		return networkAvailabilityPort.networkAvailability(productExternalId)
				.orElseThrow(() -> new ProductNotFoundException(productExternalId));
	}
}
