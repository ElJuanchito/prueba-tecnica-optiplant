package com.optiplant.inventory.purchases.infrastructure.adapter.in.web;

import com.optiplant.inventory.purchases.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.purchases.domain.exception.CancellationReasonRequiredException;
import com.optiplant.inventory.purchases.domain.exception.DiscountOutOfRangeException;
import com.optiplant.inventory.purchases.domain.exception.DuplicateOrderItemException;
import com.optiplant.inventory.purchases.domain.exception.DuplicateOrderNumberException;
import com.optiplant.inventory.purchases.domain.exception.InvalidOrderQuantityException;
import com.optiplant.inventory.purchases.domain.exception.InvalidOrderStateException;
import com.optiplant.inventory.purchases.domain.exception.InvalidUnitCostException;
import com.optiplant.inventory.purchases.domain.exception.OverReceiptNotAuthorizedException;
import com.optiplant.inventory.purchases.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderItemNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.SupplierNotActiveException;
import com.optiplant.inventory.purchases.domain.exception.SupplierNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.SupplierTaxIdAlreadyExistsException;
import com.optiplant.inventory.purchases.domain.exception.UnitConversionUnavailableException;
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
 * Maps every {@code purchases} web-layer exception to the uniform {@code { code, message }} envelope
 * of contract §7. Scoped to {@code com.optiplant.inventory.purchases.infrastructure.adapter.in} — the
 * WHOLE {@code in} package (design §6.4).
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.purchases.infrastructure.adapter.in")
public class PurchasesExceptionHandler {

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

	@ExceptionHandler(InvalidOrderQuantityException.class)
	ResponseEntity<ErrorResponse> onInvalidOrderQuantity(InvalidOrderQuantityException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_order_quantity", ex.getMessage());
	}

	@ExceptionHandler(InvalidUnitCostException.class)
	ResponseEntity<ErrorResponse> onInvalidUnitCost(InvalidUnitCostException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_unit_cost", ex.getMessage());
	}

	@ExceptionHandler(DuplicateOrderItemException.class)
	ResponseEntity<ErrorResponse> onDuplicateOrderItem(DuplicateOrderItemException ex) {
		return build(HttpStatus.BAD_REQUEST, "duplicate_order_item", ex.getMessage());
	}

	@ExceptionHandler(DiscountOutOfRangeException.class)
	ResponseEntity<ErrorResponse> onDiscountOutOfRange(DiscountOutOfRangeException ex) {
		return build(HttpStatus.BAD_REQUEST, "discount_out_of_range", ex.getMessage());
	}

	@ExceptionHandler(UnitConversionUnavailableException.class)
	ResponseEntity<ErrorResponse> onUnitConversionUnavailable(UnitConversionUnavailableException ex) {
		return build(HttpStatus.BAD_REQUEST, "unit_conversion_unavailable", ex.getMessage());
	}

	@ExceptionHandler(CancellationReasonRequiredException.class)
	ResponseEntity<ErrorResponse> onCancellationReasonRequired(CancellationReasonRequiredException ex) {
		return build(HttpStatus.BAD_REQUEST, "cancellation_reason_required", ex.getMessage());
	}

	@ExceptionHandler(BranchContextRequiredException.class)
	@ApiResponse(responseCode = "403", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onBranchContextRequired(BranchContextRequiredException ex) {
		return build(HttpStatus.FORBIDDEN, "branch_context_required", ex.getMessage());
	}

	@ExceptionHandler(OverReceiptNotAuthorizedException.class)
	ResponseEntity<ErrorResponse> onOverReceiptNotAuthorized(OverReceiptNotAuthorizedException ex) {
		return build(HttpStatus.FORBIDDEN, "over_receipt_requires_manager", ex.getMessage());
	}

	@ExceptionHandler(SupplierNotFoundException.class)
	@ApiResponse(responseCode = "404", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onSupplierNotFound(SupplierNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "supplier_not_found", ex.getMessage());
	}

	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<ErrorResponse> onProductNotFound(ProductNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "product_not_found", ex.getMessage());
	}

	@ExceptionHandler(PurchaseOrderNotFoundException.class)
	ResponseEntity<ErrorResponse> onPurchaseOrderNotFound(PurchaseOrderNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "purchase_order_not_found", ex.getMessage());
	}

	@ExceptionHandler(PurchaseOrderItemNotFoundException.class)
	ResponseEntity<ErrorResponse> onPurchaseOrderItemNotFound(PurchaseOrderItemNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "purchase_order_item_not_found", ex.getMessage());
	}

	@ExceptionHandler(SupplierTaxIdAlreadyExistsException.class)
	@ApiResponse(responseCode = "409", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onSupplierTaxIdAlreadyExists(SupplierTaxIdAlreadyExistsException ex) {
		return build(HttpStatus.CONFLICT, "supplier_tax_id_already_exists", ex.getMessage());
	}

	@ExceptionHandler(SupplierNotActiveException.class)
	ResponseEntity<ErrorResponse> onSupplierNotActive(SupplierNotActiveException ex) {
		return build(HttpStatus.CONFLICT, "supplier_not_active", ex.getMessage());
	}

	@ExceptionHandler(InvalidOrderStateException.class)
	ResponseEntity<ErrorResponse> onInvalidOrderState(InvalidOrderStateException ex) {
		return build(HttpStatus.CONFLICT, "invalid_order_state", ex.getMessage());
	}

	@ExceptionHandler(DuplicateOrderNumberException.class)
	ResponseEntity<ErrorResponse> onDuplicateOrderNumber(DuplicateOrderNumberException ex) {
		return build(HttpStatus.CONFLICT, "duplicate_order_number", ex.getMessage());
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<ErrorResponse> onConcurrentOrderUpdate(PessimisticLockingFailureException ex) {
		return build(HttpStatus.CONFLICT, "concurrent_order_update", "Could not acquire the purchase order lock in time");
	}

	@ExceptionHandler(StockMutationRejectedException.class)
	ResponseEntity<ErrorResponse> onStockMutationRejected(StockMutationRejectedException ex) {
		return switch (ex.reason()) {
			case UNIT_COST_CONTRACT -> build(HttpStatus.BAD_REQUEST, "invalid_unit_cost", ex.getMessage());
			case UNKNOWN_PRODUCT -> build(HttpStatus.NOT_FOUND, "product_not_found", ex.getMessage());
			case UNKNOWN_BRANCH -> build(HttpStatus.NOT_FOUND, "purchase_order_not_found", ex.getMessage());
			case INSUFFICIENT_STOCK -> build(HttpStatus.CONFLICT, "invalid_order_state", ex.getMessage());
		};
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	public record ErrorResponse(String code, String message) {
	}
}
