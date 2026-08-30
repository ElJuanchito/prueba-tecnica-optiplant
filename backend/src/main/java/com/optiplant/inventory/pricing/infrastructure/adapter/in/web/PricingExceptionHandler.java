package com.optiplant.inventory.pricing.infrastructure.adapter.in.web;

import com.optiplant.inventory.pricing.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.DiscountCapExceededException;
import com.optiplant.inventory.pricing.domain.exception.PriceListCodeAlreadyExistsException;
import com.optiplant.inventory.pricing.domain.exception.PriceListNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.PriceNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.PricePeriodConflictException;
import com.optiplant.inventory.pricing.domain.exception.ProductNotFoundException;
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
 * Maps every {@code pricing} web-layer exception to the uniform {@code { code, message }} envelope
 * of contract §7. Scoped to {@code com.optiplant.inventory.pricing.infrastructure.adapter.in.web}.
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.pricing.infrastructure.adapter.in.web")
public class PricingExceptionHandler {

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

	@ExceptionHandler(DiscountCapExceededException.class)
	ResponseEntity<ErrorResponse> onDiscountCapExceeded(DiscountCapExceededException ex) {
		return build(HttpStatus.BAD_REQUEST, "discount_exceeds_cap", ex.getMessage());
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

	@ExceptionHandler(PriceListNotFoundException.class)
	ResponseEntity<ErrorResponse> onPriceListNotFound(PriceListNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "price_list_not_found", ex.getMessage());
	}

	@ExceptionHandler(PriceNotFoundException.class)
	ResponseEntity<ErrorResponse> onPriceNotFound(PriceNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "price_not_found", ex.getMessage());
	}

	@ExceptionHandler(PriceListCodeAlreadyExistsException.class)
	@ApiResponse(responseCode = "409", description = "Uniform { code, message } error envelope (contract §7)",
			content = @Content(mediaType = ERROR_ENVELOPE_MEDIA_TYPE,
					schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ErrorResponse> onPriceListCodeAlreadyExists(PriceListCodeAlreadyExistsException ex) {
		return build(HttpStatus.CONFLICT, "price_list_code_already_exists", ex.getMessage());
	}

	@ExceptionHandler(PricePeriodConflictException.class)
	ResponseEntity<ErrorResponse> onPricePeriodConflict(PricePeriodConflictException ex) {
		return build(HttpStatus.CONFLICT, "price_period_conflict", ex.getMessage());
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	record ErrorResponse(String code, String message) {
	}
}
