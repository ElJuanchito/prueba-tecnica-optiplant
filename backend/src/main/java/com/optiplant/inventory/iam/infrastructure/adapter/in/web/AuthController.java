package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.iam.application.port.in.AuthenticateUseCase;
import com.optiplant.inventory.iam.application.port.in.LogoutUseCase;
import com.optiplant.inventory.iam.application.port.in.RefreshSessionUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/auth/**} — login and refresh are {@code permitAll} (wired in {@code
 * SecurityConfig}), logout requires the bearer token issued at login. Exception
 * mapping lives in {@link IamExceptionHandler}, shared by all three endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticateUseCase authenticateUseCase;
	private final RefreshSessionUseCase refreshSessionUseCase;
	private final LogoutUseCase logoutUseCase;

	public AuthController(AuthenticateUseCase authenticateUseCase, RefreshSessionUseCase refreshSessionUseCase,
			LogoutUseCase logoutUseCase) {
		this.authenticateUseCase = authenticateUseCase;
		this.refreshSessionUseCase = refreshSessionUseCase;
		this.logoutUseCase = logoutUseCase;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		AuthenticateUseCase.LoginResult result = authenticateUseCase
				.login(new AuthenticateUseCase.LoginCommand(request.username(), request.password(),
						httpRequest.getRemoteAddr()));

		return new LoginResponse(result.accessToken(), result.refreshToken(), result.expiresInSeconds(),
				result.role(), result.branchId(), result.branchName(), result.branchCode());
	}

	@PostMapping("/refresh")
	public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
		RefreshSessionUseCase.RefreshResult result = refreshSessionUseCase.refresh(request.refreshToken());
		return new RefreshResponse(result.accessToken(), result.refreshToken(), result.expiresInSeconds());
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
		logoutUseCase.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record LoginResponse(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId, String branchName, String branchCode) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record RefreshResponse(String accessToken, String refreshToken, long expiresInSeconds) {
	}

	public record LogoutRequest(@NotBlank String refreshToken) {
	}
}
