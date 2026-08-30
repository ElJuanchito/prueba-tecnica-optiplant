package com.optiplant.inventory.pricing.infrastructure.adapter.in.web;

import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase;
import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase.QuoteCommand;
import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase.QuoteItemCommand;
import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase.QuoteItemResult;
import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase.QuoteResult;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/pricing/quotes} — quote calculation endpoint (CU-VEN-02 preload, contract §6).
 */
@RestController
@RequestMapping("/api/pricing/quotes")
public class PricingQuoteController {

	private final QuotePricesUseCase quotePricesUseCase;
	private final PrincipalAccessor principalAccessor;

	public PricingQuoteController(QuotePricesUseCase quotePricesUseCase, PrincipalAccessor principalAccessor) {
		this.quotePricesUseCase = quotePricesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<QuoteItemCommand> items = request.items() == null ? List.of() : request.items().stream()
				.map(item -> new QuoteItemCommand(
						item.productExternalId(),
						item.quantity(),
						item.discountPercent()
				))
				.toList();

		QuoteResult result = quotePricesUseCase.quote(actor, new QuoteCommand(request.priceListExternalId(), items));
		return toQuoteResponse(result);
	}

	private static QuoteResponse toQuoteResponse(QuoteResult result) {
		List<QuoteItemResponse> items = result.items().stream()
				.map(item -> new QuoteItemResponse(
						item.productExternalId(),
						item.listUnitPrice(),
						item.unitPrice(),
						item.subtotal()
				))
				.toList();
		return new QuoteResponse(
				result.priceListExternalId(),
				result.code(),
				result.maxDiscountPercent(),
				items
		);
	}

	public record QuoteRequest(
			UUID priceListExternalId,
			@NotEmpty List<@Valid QuoteItemRequest> items
	) {
	}

	public record QuoteItemRequest(
			@NotNull UUID productExternalId,
			@NotNull BigDecimal quantity,
			BigDecimal discountPercent
	) {
	}

	public record QuoteItemResponse(
			UUID productExternalId,
			BigDecimal listUnitPrice,
			BigDecimal unitPrice,
			BigDecimal subtotal
	) {
	}

	public record QuoteResponse(
			UUID priceListExternalId,
			String code,
			BigDecimal maxDiscountPercent,
			List<QuoteItemResponse> items
	) {
	}
}
