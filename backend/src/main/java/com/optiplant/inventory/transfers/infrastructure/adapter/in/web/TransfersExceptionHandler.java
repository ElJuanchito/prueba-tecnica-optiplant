package com.optiplant.inventory.transfers.infrastructure.adapter.in.web;

import com.optiplant.inventory.transfers.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.transfers.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException;
import com.optiplant.inventory.transfers.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.SameBranchTransferException;
import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException;
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
 * Maps every {@code transfers} web-layer exception to the uniform {@code { code, message }}
 * envelope of contract §7. Scoped to this package only ({@code basePackages}) — mirrors
 * {@code InventoryExceptionHandler} / {@code CatalogExceptionHandler} — so neither module's
 * advice can swallow the other's exceptions.
 *
 * <p>{@link StockMutationRejectedException} (D-4) is the one exception this handler receives
 * from {@code shared} rather than from this module's own domain: {@code inventory}'s
 * {@code StockMutationAdapter} translates its internal failures into it so R-12's
 * {@code insufficient_stock} is reachable without importing {@code inventory}'s own exception
 * type across the module boundary.
 *
 * <p>{@code @ApiResponse} on one representative handler per distinct status is enough:
 * springdoc merges every documented status this advice's handlers cover into each operation the
 * advice applies to (RNF-API-01) — the same technique {@code InventoryExceptionHandler} and
 * {@code CatalogExceptionHandler} already use, so it needs no repetition per handler.
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.transfers.infrastructure.adapter.in.web")
class TransfersExceptionHandler {

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

	@ExceptionHandler(SameBranchTransferException.class)
	ResponseEntity<ErrorResponse> onSameBranchTransfer(SameBranchTransferException ex) {
		return build(HttpStatus.BAD_REQUEST, "same_branch_transfer", ex.getMessage());
	}

	@ExceptionHandler(DuplicateTransferItemException.class)
	ResponseEntity<ErrorResponse> onDuplicateTransferItem(DuplicateTransferItemException ex) {
		return build(HttpStatus.BAD_REQUEST, "duplicate_transfer_item", ex.getMessage());
	}

	@ExceptionHandler(TransferReasonRequiredException.class)
	ResponseEntity<ErrorResponse> onTransferReasonRequired(TransferReasonRequiredException ex) {
		return build(HttpStatus.BAD_REQUEST, "transfer_reason_required", ex.getMessage());
	}

	@ExceptionHandler(InvalidTransferQuantityException.class)
	ResponseEntity<ErrorResponse> onInvalidTransferQuantity(InvalidTransferQuantityException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_transfer_quantity", ex.getMessage());
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

	@ExceptionHandler(BranchNotFoundException.class)
	ResponseEntity<ErrorResponse> onBranchNotFound(BranchNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "branch_not_found", ex.getMessage());
	}

	@ExceptionHandler(TransferNotFoundException.class)
	ResponseEntity<ErrorResponse> onTransferNotFound(TransferNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "transfer_not_found", ex.getMessage());
	}

	@ExceptionHandler(TransferItemNotFoundException.class)
	ResponseEntity<ErrorResponse> onTransferItemNotFound(TransferItemNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "transfer_item_not_found", ex.getMessage());
	}

	@ExceptionHandler(InvalidTransferStateException.class)
	@ApiResponse(responseCode = "409", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onInvalidTransferState(InvalidTransferStateException ex) {
		return build(HttpStatus.CONFLICT, "invalid_transfer_state", ex.getMessage());
	}

	/** D-4 — {@code inventory}'s stock port refusal, translated across the module boundary. */
	@ExceptionHandler(StockMutationRejectedException.class)
	@ApiResponse(responseCode = "500", description = "Uniform { code, message } error envelope (contract §7) — "
			+ "UNIT_COST_CONTRACT, never expected in normal operation",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE, schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onStockMutationRejected(StockMutationRejectedException ex) {
		return switch (ex.reason()) {
			case INSUFFICIENT_STOCK -> build(HttpStatus.CONFLICT, "insufficient_stock", ex.getMessage());
			case UNKNOWN_BRANCH -> build(HttpStatus.NOT_FOUND, "branch_not_found", ex.getMessage());
			case UNKNOWN_PRODUCT -> build(HttpStatus.NOT_FOUND, "product_not_found", ex.getMessage());
			case UNIT_COST_CONTRACT -> build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
					"Unexpected unit cost contract violation");
		};
	}

	/**
	 * The pessimistic lock on the transfer row could not be acquired within the statement timeout
	 * (T-02, design §7). {@code CannotAcquireLockException} is a subclass, so it is caught here too.
	 */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<ErrorResponse> onConcurrentTransferUpdate(PessimisticLockingFailureException ex) {
		return build(HttpStatus.CONFLICT, "concurrent_transfer_update", "Could not acquire the transfer lock in time");
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	record ErrorResponse(String code, String message) {
	}
}
