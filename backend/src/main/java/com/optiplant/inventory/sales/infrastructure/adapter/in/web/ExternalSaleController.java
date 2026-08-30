package com.optiplant.inventory.sales.infrastructure.adapter.in.web;

import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase;
import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleCommand;
import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleItemCommand;
import com.optiplant.inventory.sales.domain.model.InvoiceNumber;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.infrastructure.adapter.in.web.SaleController.RegisterSaleItemRequest;
import com.optiplant.inventory.sales.infrastructure.adapter.in.web.SaleController.SaleDetailResponse;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/external/sales} — external POS intake adapter (CU-EXT-02, design §6.5, P-07).
 *
 * <p>Invokes the same {@link RegisterSaleUseCase} without duplicating domain rules.
 * A branch field in the payload, or an invoice number matching the internal pattern {@code VEN-},
 * is rejected with {@code 400 invalid_request} (R-27, D-5).
 */
@RestController
@RequestMapping("/api/external/sales")
public class ExternalSaleController {

	private final RegisterSaleUseCase registerSaleUseCase;
	private final PrincipalAccessor principalAccessor;

	public ExternalSaleController(RegisterSaleUseCase registerSaleUseCase, PrincipalAccessor principalAccessor) {
		this.registerSaleUseCase = registerSaleUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public ResponseEntity<SaleDetailResponse> registerExternalSale(@Valid @RequestBody ExternalSaleRequest request) {
		if (request.branchExternalId() != null || request.branchId() != null || request.branch() != null) {
			throw new IllegalArgumentException("Branch must not be specified in external sale payload");
		}

		InvoiceNumber invoiceNumber = new InvoiceNumber(request.invoiceNumber());
		if (invoiceNumber.isReservedInternal()) {
			throw new IllegalArgumentException("Invoice number matches reserved internal pattern");
		}

		AuthenticatedPrincipal actor = principalAccessor.require();
		List<RegisterSaleItemCommand> items = request.items() == null ? List.of() : request.items().stream()
				.map(item -> new RegisterSaleItemCommand(
						item.productExternalId(),
						item.quantity(),
						item.unitOfMeasureExternalId(),
						item.discountPercent()
				))
				.toList();

		RegisterSaleCommand command = new RegisterSaleCommand(
				request.priceListExternalId(),
				request.customerName(),
				request.customerTaxId(),
				request.taxPercent(),
				request.notes(),
				items,
				invoiceNumber.value()
		);

		SaleDetail detail = registerSaleUseCase.register(actor, command);
		return ResponseEntity.status(HttpStatus.CREATED).body(SaleController.toDetailResponse(detail));
	}

	public record ExternalSaleRequest(
			UUID priceListExternalId,
			@NotBlank String customerName,
			String customerTaxId,
			BigDecimal taxPercent,
			String notes,
			@NotEmpty List<@Valid RegisterSaleItemRequest> items,
			@NotBlank String invoiceNumber,
			UUID branchExternalId,
			UUID branchId,
			String branch
	) {
	}
}
