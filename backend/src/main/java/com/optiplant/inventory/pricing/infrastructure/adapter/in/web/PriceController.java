package com.optiplant.inventory.pricing.infrastructure.adapter.in.web;

import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase;
import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase.ClosePriceCommand;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.infrastructure.adapter.in.web.PriceListController.PriceResponse;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/pricing/prices/**} — individual price row management (R-16, contract §6).
 */
@RestController
@RequestMapping("/api/pricing/prices")
public class PriceController {

	private final ManagePricesUseCase managePricesUseCase;
	private final PrincipalAccessor principalAccessor;

	public PriceController(ManagePricesUseCase managePricesUseCase, PrincipalAccessor principalAccessor) {
		this.managePricesUseCase = managePricesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PatchMapping("/{externalId}/closure")
	public PriceResponse closePrice(
			@PathVariable UUID externalId,
			@RequestBody(required = false) ClosePriceRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		LocalDate validTo = request != null ? request.validTo() : null;
		Price price = managePricesUseCase.closePrice(actor, externalId, new ClosePriceCommand(validTo));
		return PriceListController.toPriceResponse(price);
	}

	public record ClosePriceRequest(LocalDate validTo) {
	}
}
