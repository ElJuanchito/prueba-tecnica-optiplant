package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.iam.application.port.in.AuthenticateUseCase;
import com.optiplant.inventory.iam.domain.exception.InvalidCredentialsException;
import com.optiplant.inventory.iam.domain.exception.TooManyLoginAttemptsException;
import com.optiplant.inventory.iam.domain.exception.UserDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/auth/login} — permitAll (wired in {@code SecurityConfig}); the
 * exception mapping stays local to this controller for slice 2a. Slice 2b's
 * {@code IamExceptionHandler} takes over once {@code /refresh} and {@code /logout}
 * exist and need the same mapping.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticateUseCase authenticateUseCase;

	public AuthController(AuthenticateUseCase authenticateUseCase) {
		this.authenticateUseCase = authenticateUseCase;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		AuthenticateUseCase.LoginResult result = authenticateUseCase
				.login(new AuthenticateUseCase.LoginCommand(request.username(), request.password(),
						httpRequest.getRemoteAddr()));

		return new LoginResponse(result.accessToken(), result.refreshToken(), result.expiresInSeconds(),
				result.role(), result.branchId());
	}

	// Same response for both: neither may reveal whether the username exists
	// (CU-SEG-01 EX-01) or is merely disabled (EX-02).
	@ExceptionHandler({ InvalidCredentialsException.class, UserDisabledException.class })
	public ResponseEntity<ErrorResponse> onInvalidCredentials() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("invalid_credentials", "Invalid username or password"));
	}

	@ExceptionHandler(TooManyLoginAttemptsException.class)
	public ResponseEntity<ErrorResponse> onTooManyAttempts() {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("too_many_attempts", "Too many login attempts, try again later"));
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record LoginResponse(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId) {
	}

	public record ErrorResponse(String code, String message) {
	}
}
