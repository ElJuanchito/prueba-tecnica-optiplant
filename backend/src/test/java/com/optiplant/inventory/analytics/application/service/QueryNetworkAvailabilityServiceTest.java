package com.optiplant.inventory.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.analytics.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.shared.availability.BranchAvailabilityView;
import com.optiplant.inventory.shared.availability.NetworkAvailabilityPort;
import com.optiplant.inventory.shared.availability.NetworkAvailabilityView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryNetworkAvailabilityServiceTest {

	private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID MISSING_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private StubNetworkAvailabilityPort availabilityPort;
	private QueryNetworkAvailabilityService service;

	@BeforeEach
	void setUp() {
		availabilityPort = new StubNetworkAvailabilityPort();
		service = new QueryNetworkAvailabilityService(availabilityPort);
	}

	@Test
	@DisplayName("CU-EXT-01: returns availability view when product exists")
	void returnsViewWhenProductExists() {
		NetworkAvailabilityView expected = new NetworkAvailabilityView(
				PRODUCT_ID, "SKU-1", "Product 1",
				List.of(new BranchAvailabilityView(UUID.randomUUID(), "Branch 1",
						new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10"))),
				new BigDecimal("10")
		);
		availabilityPort.view = expected;

		NetworkAvailabilityView result = service.availability(PRODUCT_ID);

		assertThat(result).isEqualTo(expected);
	}

	@Test
	@DisplayName("R-24/D-2: throws ProductNotFoundException when port returns Optional.empty()")
	void throwsProductNotFoundWhenMissing() {
		assertThatThrownBy(() -> service.availability(MISSING_PRODUCT))
				.isInstanceOf(ProductNotFoundException.class);
	}

	private static class StubNetworkAvailabilityPort implements NetworkAvailabilityPort {
		NetworkAvailabilityView view;

		@Override
		public Optional<NetworkAvailabilityView> networkAvailability(UUID productExternalId) {
			if (productExternalId.equals(PRODUCT_ID)) {
				return Optional.ofNullable(view);
			}
			return Optional.empty();
		}
	}
}
