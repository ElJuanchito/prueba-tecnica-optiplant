package com.optiplant.inventory.inventory.infrastructure.adapter.in.web;

import com.optiplant.inventory.inventory.domain.exception.AdjustmentReasonRequiredException;
import com.optiplant.inventory.inventory.domain.exception.AdjustmentWithoutDifferenceException;
import com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.inventory.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.inventory.domain.exception.InsufficientStockException;
import com.optiplant.inventory.inventory.domain.exception.InventoryRecordNotFoundException;
import com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.inventory.domain.exception.UnitCostContractViolationException;
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
 * Maps every {@code inventory} web-layer exception to the uniform {@code { code, message }}
 * envelope of contract §7. Scoped to this package only — mirrors
 * {@code CatalogExceptionHandler} — so neither module's advice can swallow the other's
 * exceptions.
 *
 * <p>{@code inventory_record_not_found} is reachable through {@link InventoryRecordNotFoundException},
 * raised as a defensive check by {@code BranchInventoryPersistenceAdapter#save} when the row a
 * caller already locked or created in this same transaction has vanished by the time the
 * mutation writes back — a data-integrity guard, not a normal outcome, since every mutating
 * flow otherwise creates the row on demand (F-3).
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.inventory.infrastructure.adapter.in.web")
class InventoryExceptionHandler {

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

	/** P-03's cost-presence rule (design §3.3) — its own type so a genuine 500 isn't hidden. */
	@ExceptionHandler(UnitCostContractViolationException.class)
	ResponseEntity<ErrorResponse> onUnitCostContractViolation(UnitCostContractViolationException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_request", ex.getMessage());
	}

	@ExceptionHandler(AdjustmentReasonRequiredException.class)
	@ApiResponse(responseCode = "400", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onAdjustmentReasonRequired(AdjustmentReasonRequiredException ex) {
		return build(HttpStatus.BAD_REQUEST, "adjustment_reason_required", ex.getMessage());
	}

	@ExceptionHandler(AdjustmentWithoutDifferenceException.class)
	ResponseEntity<ErrorResponse> onAdjustmentWithoutDifference(AdjustmentWithoutDifferenceException ex) {
		return build(HttpStatus.BAD_REQUEST, "adjustment_without_difference", ex.getMessage());
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
	ResponseEntity<ErrorResponse> onProductNotFound() {
		return build(HttpStatus.NOT_FOUND, "product_not_found", "Product not found");
	}

	@ExceptionHandler(InventoryRecordNotFoundException.class)
	ResponseEntity<ErrorResponse> onInventoryRecordNotFound(InventoryRecordNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "inventory_record_not_found", ex.getMessage());
	}

	@ExceptionHandler(InsufficientStockException.class)
	@ApiResponse(responseCode = "409", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onInsufficientStock(InsufficientStockException ex) {
		return build(HttpStatus.CONFLICT, "insufficient_stock", ex.getMessage());
	}

	/**
	 * The pessimistic lock could not be acquired within the statement timeout (T-02, design §7).
	 * {@code CannotAcquireLockException} is a subclass, so it is caught here too.
	 */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<ErrorResponse> onConcurrentStockUpdate(PessimisticLockingFailureException ex) {
		return build(HttpStatus.CONFLICT, "concurrent_stock_update", "Could not acquire the stock lock in time");
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	record ErrorResponse(String code, String message) {
	}
}
