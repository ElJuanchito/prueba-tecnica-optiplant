package com.optiplant.inventory.logistics.infrastructure.adapter.in.web;

import com.optiplant.inventory.logistics.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.logistics.domain.exception.RouteAlreadyExistsException;
import com.optiplant.inventory.logistics.domain.exception.RouteNotFoundException;
import com.optiplant.inventory.logistics.domain.exception.SameBranchRouteException;
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
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.logistics.infrastructure.adapter.in.web")
class LogisticsExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
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
	ResponseEntity<ErrorResponse> onBranchNotFound(BranchNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "branch_not_found", ex.getMessage());
	}

	@ExceptionHandler(RouteNotFoundException.class)
	ResponseEntity<ErrorResponse> onRouteNotFound(RouteNotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, "route_not_found", ex.getMessage());
	}

	@ExceptionHandler(RouteAlreadyExistsException.class)
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
