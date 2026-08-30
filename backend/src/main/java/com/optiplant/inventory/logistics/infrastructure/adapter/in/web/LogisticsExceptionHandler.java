package com.optiplant.inventory.logistics.infrastructure.adapter.in.web;

import com.optiplant.inventory.logistics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.logistics.domain.exception.RouteAlreadyExistsException;
import com.optiplant.inventory.logistics.domain.exception.RouteNotFoundException;
import com.optiplant.inventory.logistics.domain.exception.SameBranchRouteException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every {@code logistics} web-layer exception to the uniform {@code { code, message }}
 * envelope of contract §7. Scoped to this package only ({@code basePackages}) — mirrors
 * {@code TransfersExceptionHandler} — so neither module's advice can swallow the other's
 * exceptions.
 *
 * <p>{@code @ApiResponse} on one representative handler per distinct status is enough:
 * springdoc merges every documented status this advice's handlers cover into each operation the
 * advice applies to (RNF-API-01) — the same technique {@code InventoryExceptionHandler} and
 * {@code CatalogExceptionHandler} already use, so it needs no repetition per handler.
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.logistics.infrastructure.adapter.in.web")
class LogisticsExceptionHandler {

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

	/** R-23 — {@code SameBranchRouteException}'s own Javadoc names {@code invalid_request}, not a dedicated code. */
	@ExceptionHandler(SameBranchRouteException.class)
	ResponseEntity<ErrorResponse> onSameBranchRoute(SameBranchRouteException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_request", ex.getMessage());
	}

	@ExceptionHandler(BranchNotFoundException.class)
	@ApiResponse(responseCode = "404", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onBranchNotFound(BranchNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "branch_not_found", ex.getMessage());
	}

	@ExceptionHandler(RouteNotFoundException.class)
	ResponseEntity<ErrorResponse> onRouteNotFound(RouteNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "route_not_found", ex.getMessage());
	}

	@ExceptionHandler(RouteAlreadyExistsException.class)
	@ApiResponse(responseCode = "409", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onRouteAlreadyExists(RouteAlreadyExistsException ex) {
		return build(HttpStatus.CONFLICT, "route_already_exists", ex.getMessage());
	}

	/** A concurrent insert can still race {@code existsForPair}'s check past {@code uq_route_pair} (R-23). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> onDataIntegrityViolation(DataIntegrityViolationException ex) {
		return build(HttpStatus.CONFLICT, "route_already_exists", "A route already exists for this ordered branch pair");
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	record ErrorResponse(String code, String message) {
	}
}
