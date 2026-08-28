package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException;
import com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException;
import com.optiplant.inventory.iam.domain.exception.InvalidCredentialsException;
import com.optiplant.inventory.iam.domain.exception.RefreshTokenRejectedException;
import com.optiplant.inventory.iam.domain.exception.TooManyLoginAttemptsException;
import com.optiplant.inventory.iam.domain.exception.UserDisabledException;
import com.optiplant.inventory.iam.domain.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps every {@code iam} web-layer exception to its HTTP response, shared by {@link
 * AuthController}'s three endpoints (extracted from {@code AuthController} in slice
 * 2b, once {@code /refresh} and {@code /logout} needed the same mapping too).
 *
 * <p>Scoped to this controller's package only — it must not intercept exceptions
 * thrown by future modules' controllers.
 */
@RestControllerAdvice(basePackages = "com.optiplant.inventory.iam.infrastructure.adapter.in.web")
class IamExceptionHandler {

	// Same response for both: neither may reveal whether the username exists
	// (CU-SEG-01 EX-01) or is merely disabled (EX-02).
	@ExceptionHandler({ InvalidCredentialsException.class, UserDisabledException.class })
	ResponseEntity<ErrorResponse> onInvalidCredentials() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("invalid_credentials", "Invalid username or password"));
	}

	@ExceptionHandler(TooManyLoginAttemptsException.class)
	ResponseEntity<ErrorResponse> onTooManyAttempts() {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("too_many_attempts", "Too many login attempts, try again later"));
	}

	// Not found / reuse / expired / idle all collapse to the same generic 401 — a
	// distinguishable response would tell a caller which guess got closer.
	@ExceptionHandler(RefreshTokenRejectedException.class)
	ResponseEntity<ErrorResponse> onRefreshTokenRejected() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("invalid_refresh_token", "Invalid or expired refresh token"));
	}

	// The caller is already authenticated, so unlike the exceptions above there is
	// no existence-leak concern here — a distinct 403 does not reveal anything new
	// (branch-isolation "Cross-branch mutation is rejected").
	@ExceptionHandler(CrossBranchMutationException.class)
	ResponseEntity<ErrorResponse> onCrossBranchMutation() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("cross_branch_mutation", "Cannot mutate a resource of another branch"));
	}

	// user-administration "Duplicate username" / "Duplicate email": the system
	// must reject the operation, indicating the conflict — no existence-leak
	// concern here (the caller is an already-authenticated ADMIN who supplied
	// the colliding value itself).
	@ExceptionHandler(DuplicateUsernameException.class)
	ResponseEntity<ErrorResponse> onDuplicateUsername(DuplicateUsernameException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("duplicate_user_field", ex.getMessage()));
	}

	@ExceptionHandler(UserNotFoundException.class)
	ResponseEntity<ErrorResponse> onUserNotFound() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("user_not_found", "User not found"));
	}

	// Role/branch validation (user-administration "Non-ADMIN role without a
	// branch") and an unresolvable branch external_id both surface as a plain
	// IllegalArgumentException from UserAdminService/UserPersistenceAdapter —
	// scoped to this package's advice only, so it cannot swallow an
	// IllegalArgumentException thrown by some future module's controller.
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> onIllegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("invalid_request", ex.getMessage()));
	}

	record ErrorResponse(String code, String message) {
	}
}
