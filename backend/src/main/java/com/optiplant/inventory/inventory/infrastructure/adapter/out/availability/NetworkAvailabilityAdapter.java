package com.optiplant.inventory.inventory.infrastructure.adapter.out.availability;

import com.optiplant.inventory.inventory.application.port.in.QueryStockUseCase;
import com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.inventory.domain.model.NetworkAvailability;
import com.optiplant.inventory.shared.availability.BranchAvailabilityView;
import com.optiplant.inventory.shared.availability.NetworkAvailabilityPort;
import com.optiplant.inventory.shared.availability.NetworkAvailabilityView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Single {@link NetworkAvailabilityPort} implementation in {@code inventory} (contract P-03, design §2, D-3).
 * Delegates to {@link QueryStockUseCase#networkAvailability(UUID)} and maps {@code inventory}'s domain
 * records to the {@code shared} view records, deliberately dropping the {@code isOwnBranch} marker (R-24, D-1).
 */
@Component
public class NetworkAvailabilityAdapter implements NetworkAvailabilityPort {

	private final QueryStockUseCase queryStockUseCase;

	public NetworkAvailabilityAdapter(QueryStockUseCase queryStockUseCase) {
		this.queryStockUseCase = queryStockUseCase;
	}

	@Override
	public Optional<NetworkAvailabilityView> networkAvailability(UUID productExternalId) {
		try {
			NetworkAvailability availability = queryStockUseCase.networkAvailability(productExternalId);
			List<BranchAvailabilityView> branches = availability.branches().stream()
					.map(b -> new BranchAvailabilityView(
							b.branchExternalId(),
							b.branchName(),
							b.currentStock(),
							b.reservedStock(),
							b.inTransitStock(),
							b.availableStock()))
					.toList();
			return Optional.of(new NetworkAvailabilityView(
					availability.productExternalId(),
					availability.sku(),
					availability.name(),
					branches,
					availability.networkTotal()));
		} catch (ProductNotFoundException ex) {
			return Optional.empty();
		}
	}
}
