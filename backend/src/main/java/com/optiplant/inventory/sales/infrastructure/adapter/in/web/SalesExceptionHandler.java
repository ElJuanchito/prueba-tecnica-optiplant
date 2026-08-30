package com.optiplant.inventory.sales.infrastructure.adapter.in.web;

import com.optiplant.inventory.sales.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.sales.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.sales.domain.exception.DiscountExceedsCapException;
import com.optiplant.inventory.sales.domain.exception.DuplicateInvoiceNumberException;
import com.optiplant.inventory.sales.domain.exception.DuplicateSaleItemException;
import com.optiplant.inventory.sales.domain.exception.InvalidSaleQuantityException;
import com.optiplant.inventory.sales.domain.exception.InvalidSaleStateException;
import com.optiplant.inventory.sales.domain.exception.PriceListNotFoundException;
import com.optiplant.inventory.sales.domain.exception.PriceListNotResolvableException;
import com.optiplant.inventory.sales.domain.exception.PriceNotAvailableException;
import com.optiplant.inventory.sales.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.sales.domain.exception.SaleNotFoundException;
import com.optiplant.inventory.sales.domain.exception.SaleReasonRequiredException;
import com.optiplant.inventory.sales.domain.exception.UnitConversionUnavailableException;
import com.optiplant.inventory.shared.stock.StockMutationRejectedException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every {@code sales} web-layer exception to the uniform {@code { code, message }} envelope
 * of contract §7. Scoped to {@code com.optiplant.inventory.sales.infrastructure.adapter.in} — the
 * WHOLE {@code in} package (design §6.4 trap) — so both internal and external controllers are covered.
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.sales.infrastructure.adapter.in")
public class SalesExceptionHandler {

	private static final String ERROR_ENVELOPE_MEDIA_TYPE = "application/json";

	@ExceptionHandler(IllegalArgumentException.class)
	@ApiResponse(responseCode = "400", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onIllegalArgument(IllegalArgumentException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_request", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> onBeanValidation(MethodArgumentNotValidException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_request", "Request body failed validation");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> onTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_request", "Malformed path or query parameter");
	}

	@ExceptionHandler(InvalidSaleQuantityException.class)
	ResponseEntity<ErrorResponse> onInvalidSaleQuantity(InvalidSaleQuantityException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_sale_quantity", ex.getMessage());
	}

	@ExceptionHandler(DuplicateSaleItemException.class)
	ResponseEntity<ErrorResponse> onDuplicateSaleItem(DuplicateSaleItemException ex) {
		return build(HttpStatus.BAD_REQUEST, "duplicate_sale_item", ex.getMessage());
	}

	@ExceptionHandler(DiscountExceedsCapException.class)
	ResponseEntity<ErrorResponse> onDiscountExceedsCap(DiscountExceedsCapException ex) {
		return build(HttpStatus.BAD_REQUEST, "discount_exceeds_cap", ex.getMessage());
	}

	@ExceptionHandler(UnitConversionUnavailableException.class)
	ResponseEntity<ErrorResponse> onUnitConversionUnavailable(UnitConversionUnavailableException ex) {
		return build(HttpStatus.BAD_REQUEST, "unit_conversion_unavailable", ex.getMessage());
	}

	@ExceptionHandler(SaleReasonRequiredException.class)
	ResponseEntity<ErrorResponse> onSaleReasonRequired(SaleReasonRequiredException ex) {
		return build(HttpStatus.BAD_REQUEST, "sale_reason_required", ex.getMessage());
	}

	@ExceptionHandler(BranchContextRequiredException.class)
	@ApiResponse(responseCode = "403", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onBranchContextRequired(BranchContextRequiredException ex) {
		return build(HttpStatus.FORBIDDEN, "branch_context_required", ex.getMessage());
	}

	@ExceptionHandler(CrossBranchAccessDeniedException.class)
	ResponseEntity<ErrorResponse> onCrossBranchAccessDenied(CrossBranchAccessDeniedException ex) {
		return build(HttpStatus.FORBIDDEN, "cross_branch_access_denied", ex.getMessage());
	}

	@ExceptionHandler(ProductNotFoundException.class)
	@ApiResponse(responseCode = "404", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onProductNotFound(ProductNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "product_not_found", ex.getMessage());
	}

	@ExceptionHandler(PriceListNotFoundException.class)
	ResponseEntity<ErrorResponse> onPriceListNotFound(PriceListNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "price_list_not_found", ex.getMessage());
	}

	@ExceptionHandler(SaleNotFoundException.class)
	ResponseEntity<ErrorResponse> onSaleNotFound(SaleNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "sale_not_found", ex.getMessage());
	}

	@ExceptionHandler(InvalidSaleStateException.class)
	@ApiResponse(responseCode = "409", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onInvalidSaleState(InvalidSaleStateException ex) {
		return build(HttpStatus.CONFLICT, "invalid_sale_state", ex.getMessage());
	}

	@ExceptionHandler(PriceListNotResolvableException.class)
	ResponseEntity<ErrorResponse> onPriceListNotResolvable(PriceListNotResolvableException ex) {
		return build(HttpStatus.CONFLICT, "price_list_not_resolvable", ex.getMessage());
	}

	@ExceptionHandler(PriceNotAvailableException.class)
	ResponseEntity<ErrorResponse> onPriceNotAvailable(PriceNotAvailableException ex) {
		return build(HttpStatus.CONFLICT, "price_not_available", ex.getMessage());
	}

	@ExceptionHandler(DuplicateInvoiceNumberException.class)
	ResponseEntity<ErrorResponse> onDuplicateInvoiceNumber(DuplicateInvoiceNumberException ex) {
		return build(HttpStatus.CONFLICT, "duplicate_invoice_number", ex.getMessage());
	}

	@ExceptionHandler(StockMutationRejectedException.class)
	@ApiResponse(responseCode = "500", description = "Uniform { code, message } error envelope (contract §7) — UNIT_COST_CONTRACT",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onStockMutationRejected(StockMutationRejectedException ex) {
		return switch (ex.reason()) {
			case INSUFFICIENT_STOCK -> build(HttpStatus.CONFLICT, "insufficient_stock", ex.getMessage());
			case UNKNOWN_BRANCH -> build(HttpStatus.NOT_FOUND, "branch_not_found", ex.getMessage());
			case UNKNOWN_PRODUCT -> build(HttpStatus.NOT_FOUND, "product_not_found", ex.getMessage());
			case UNIT_COST_CONTRACT -> build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
					"Unexpected unit cost contract violation");
		};
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<ErrorResponse> onConcurrentSaleUpdate(PessimisticLockingFailureException ex) {
		return build(HttpStatus.CONFLICT, "concurrent_sale_update", "Could not acquire the sale lock in time");
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	record ErrorResponse(String code, String message) {
	}
}
