package com.optiplant.inventory.catalog.infrastructure.adapter.in.web;

import com.optiplant.inventory.catalog.domain.exception.CategoryInUseException;
import com.optiplant.inventory.catalog.domain.exception.CategoryInactiveException;
import com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException;
import com.optiplant.inventory.catalog.domain.exception.DuplicateCategoryNameException;
import com.optiplant.inventory.catalog.domain.exception.DuplicateProductUnitException;
import com.optiplant.inventory.catalog.domain.exception.DuplicateSkuException;
import com.optiplant.inventory.catalog.domain.exception.InvalidConversionFactorException;
import com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps {@code catalog} web-layer exceptions to the uniform
 * {@code { code, message }} envelope of contract §7. Scoped to this package only
 * — exactly like {@code iam}'s {@code IamExceptionHandler}
 * ({@code IamExceptionHandler.java:26}) — so neither module's advice can swallow
 * the other's exceptions.
 *
 * <p>{@code BaseUnitChangeRejectedException} deliberately gets <strong>no</strong>
 * mapping: PA-08 defers the endpoint that would raise it and a code with no
 * reachable path is dead contract (design §3.4). Category and product mappings are
 * wired here (S3, S5), including {@code DuplicateProductUnitException} and
 * {@code InvalidConversionFactorException}, both reachable through
 * {@code POST /products} with inline units; {@code ProductUnitNotFoundException}
 * and the units subresource arrive with slice S6.
 *
 * <p>{@code ErrorResponse} is a local record, deliberately duplicated from
 * {@code iam}'s package-private one: it cannot be imported across the module
 * boundary and promoting it to {@code shared/web} would widen this change into
 * {@code iam}, which contract §2.1 caps at one point (design §6.3).
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.catalog.infrastructure.adapter.in.web")
class CatalogExceptionHandler {

	// Value-object violations (CategoryName), active-filter parsing and any other
	// IllegalArgumentException from this package's controllers.
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

	@ExceptionHandler(CategoryNotFoundException.class)
	ResponseEntity<ErrorResponse> onCategoryNotFound() {
		return build(HttpStatus.NOT_FOUND, "category_not_found", "Category not found");
	}

	@ExceptionHandler(DuplicateCategoryNameException.class)
	ResponseEntity<ErrorResponse> onDuplicateCategoryName(DuplicateCategoryNameException ex) {
		return build(HttpStatus.CONFLICT, "duplicate_category_name", ex.getMessage());
	}

	@ExceptionHandler(CategoryInUseException.class)
	ResponseEntity<ErrorResponse> onCategoryInUse(CategoryInUseException ex) {
		return build(HttpStatus.CONFLICT, "category_in_use", ex.getMessage());
	}

	@ExceptionHandler(CategoryInactiveException.class)
	ResponseEntity<ErrorResponse> onCategoryInactive(CategoryInactiveException ex) {
		return build(HttpStatus.CONFLICT, "category_inactive", ex.getMessage());
	}

	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<ErrorResponse> onProductNotFound() {
		return build(HttpStatus.NOT_FOUND, "product_not_found", "Product not found");
	}

	@ExceptionHandler(DuplicateSkuException.class)
	ResponseEntity<ErrorResponse> onDuplicateSku(DuplicateSkuException ex) {
		return build(HttpStatus.CONFLICT, "duplicate_sku", ex.getMessage());
	}

	@ExceptionHandler(DuplicateProductUnitException.class)
	ResponseEntity<ErrorResponse> onDuplicateProductUnit(DuplicateProductUnitException ex) {
		return build(HttpStatus.CONFLICT, "duplicate_product_unit", ex.getMessage());
	}

	@ExceptionHandler(InvalidConversionFactorException.class)
	ResponseEntity<ErrorResponse> onInvalidConversionFactor(InvalidConversionFactorException ex) {
		return build(HttpStatus.BAD_REQUEST, "invalid_conversion_factor", ex.getMessage());
	}

	/**
	 * The database rejecting what the in-memory pre-check let through — a
	 * concurrent duplicate racing {@code uq_categories_name_ci} or the
	 * {@code products.sku} unique index ({@code products_sku_key}). Only a
	 * constraint this handler can positively name from the cause message becomes a
	 * tidy {@code 409}; anything else is rethrown and surfaces as {@code 500},
	 * because a wrong-but-tidy {@code 409} would tell the caller to fix a
	 * duplicate that does not exist (D-14). No SQL, constraint name or stack trace
	 * reaches the client (§7.1 point 2).
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> onDataIntegrityViolation(DataIntegrityViolationException ex) {
		Throwable cause = ex.getMostSpecificCause();
		String message = cause == null ? null : cause.getMessage();
		if (message != null && message.contains("uq_categories_name_ci")) {
			return build(HttpStatus.CONFLICT, "duplicate_category_name", "Category name is already in use");
		}
		if (message != null && (message.contains("products_sku_key") || message.contains("products_sku"))) {
			return build(HttpStatus.CONFLICT, "duplicate_sku", "SKU is already in use");
		}
		throw ex;
	}

	private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	record ErrorResponse(String code, String message) {
	}
}
