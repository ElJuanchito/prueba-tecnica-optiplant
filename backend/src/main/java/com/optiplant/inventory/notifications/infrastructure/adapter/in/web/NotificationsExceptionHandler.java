package com.optiplant.inventory.notifications.infrastructure.adapter.in.web;

import com.optiplant.inventory.notifications.domain.exception.AlertAlreadyResolvedException;
import com.optiplant.inventory.notifications.domain.exception.AlertNotFoundException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every {@code notifications} web-layer exception to the uniform
 * {@code { code, message }} envelope of contract §7. Scoped to this package only — mirrors
 * {@code CatalogExceptionHandler} and {@code InventoryExceptionHandler} — so no advice
 * swallows another module's exceptions.
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.notifications.infrastructure.adapter.in.web")
class NotificationsExceptionHandler {

	private static final String ERROR_ENVELOPE_MEDIA_TYPE = "application/json";

	@ExceptionHandler(IllegalArgumentException.class)
	@ApiResponse(responseCode = "400", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onIllegalArgument(IllegalArgumentException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_request", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> onTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_request", "Malformed path or query parameter");
	}

	@ExceptionHandler(AlertNotFoundException.class)
	@ApiResponse(responseCode = "404", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onAlertNotFound() {
		return build(HttpStatus.NOT_FOUND, "alert_not_found", "Alert not found");
	}

	@ExceptionHandler(AlertAlreadyResolvedException.class)
	@ApiResponse(responseCode = "409", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onAlertAlreadyResolved(AlertAlreadyResolvedException ex) {
		return build(HttpStatus.CONFLICT, "alert_already_resolved", ex.getMessage());
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	record ErrorResponse(String code, String message) {
	}
}
