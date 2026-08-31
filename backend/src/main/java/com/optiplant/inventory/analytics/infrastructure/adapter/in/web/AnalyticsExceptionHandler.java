package com.optiplant.inventory.analytics.infrastructure.adapter.in.web;

import com.optiplant.inventory.analytics.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.analytics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.analytics.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.analytics.domain.exception.ProductNotFoundException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every {@code analytics} web-layer exception to the uniform {@code { code, message }} error envelope
 * of contract §7. Scoped to {@code com.optiplant.inventory.analytics.infrastructure.adapter.in} — the whole
 * {@code in} package (design §7).
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.analytics.infrastructure.adapter.in")
public class AnalyticsExceptionHandler {

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

	@ExceptionHandler(BranchContextRequiredException.class)
	@ApiResponse(responseCode = "403", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onBranchContextRequired(BranchContextRequiredException ex) {
		return build(HttpStatus.FORBIDDEN, "branch_context_required", ex.getMessage());
	}

	@ExceptionHandler(CrossBranchAccessDeniedException.class)
	@ApiResponse(responseCode = "403", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onCrossBranchAccessDenied(CrossBranchAccessDeniedException ex) {
		return build(HttpStatus.FORBIDDEN, "cross_branch_access_denied", ex.getMessage());
	}

	@ExceptionHandler(BranchNotFoundException.class)
	@ApiResponse(responseCode = "404", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onBranchNotFound(BranchNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "branch_not_found", ex.getMessage());
	}

	@ExceptionHandler(ProductNotFoundException.class)
	@ApiResponse(responseCode = "404", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onProductNotFound(ProductNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "product_not_found", ex.getMessage());
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	public record ErrorResponse(String code, String message) {
	}
}
