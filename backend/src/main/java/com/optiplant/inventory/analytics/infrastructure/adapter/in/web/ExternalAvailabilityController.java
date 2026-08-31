package com.optiplant.inventory.analytics.infrastructure.adapter.in.web;

import com.optiplant.inventory.analytics.application.port.in.QueryNetworkAvailabilityUseCase;
import com.optiplant.inventory.shared.availability.BranchAvailabilityView;
import com.optiplant.inventory.shared.availability.NetworkAvailabilityView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for external POS/ERP network availability queries (CU-EXT-01, RF-EXT-01, contract §6).
 * Authenticated via dedicated API-key filter chain (F-6, design §7).
 * Exposes only {@code external_id} and never exposes cost, price, or valuation data (R-26).
 */
@RestController
@RequestMapping("/api/external/availability")
public class ExternalAvailabilityController {

	private final QueryNetworkAvailabilityUseCase queryNetworkAvailabilityUseCase;

	public ExternalAvailabilityController(QueryNetworkAvailabilityUseCase queryNetworkAvailabilityUseCase) {
		this.queryNetworkAvailabilityUseCase = queryNetworkAvailabilityUseCase;
	}

	@GetMapping("/{productExternalId}")
	public ExternalNetworkAvailabilityResponse networkAvailability(@PathVariable UUID productExternalId) {
		NetworkAvailabilityView view = queryNetworkAvailabilityUseCase.availability(productExternalId);

		List<ExternalBranchAvailabilityResponse> branches = view.branches().stream()
				.map(b -> new ExternalBranchAvailabilityResponse(b.branchExternalId(), b.branchName(),
						b.currentStock(), b.reservedStock(), b.inTransitStock(), b.availableStock()))
				.toList();

		return new ExternalNetworkAvailabilityResponse(view.productExternalId(), view.sku(), view.name(),
				branches, view.networkTotal());
	}

	public record ExternalBranchAvailabilityResponse(UUID branchExternalId, String branchName,
			BigDecimal currentStock, BigDecimal reservedStock, BigDecimal inTransitStock,
			BigDecimal availableStock) {
	}

	public record ExternalNetworkAvailabilityResponse(UUID productExternalId, String sku, String name,
			List<ExternalBranchAvailabilityResponse> branches, BigDecimal networkTotal) {
	}
}
