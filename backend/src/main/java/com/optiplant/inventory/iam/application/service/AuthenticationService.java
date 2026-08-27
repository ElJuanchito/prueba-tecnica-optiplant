package com.optiplant.inventory.iam.application.service;

import com.optiplant.inventory.iam.application.port.in.AuthenticateUseCase;
import com.optiplant.inventory.iam.application.port.out.AccessTokenIssuerPort;
import com.optiplant.inventory.iam.application.port.out.LoginThrottlePort;
import com.optiplant.inventory.iam.application.port.out.PasswordHasherPort;
import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.SecretTokenGeneratorPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort;
import com.optiplant.inventory.iam.domain.exception.InvalidCredentialsException;
import com.optiplant.inventory.iam.domain.exception.UserDisabledException;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates login per design's Data Flow "LOGIN": throttle → load active user →
 * verify password → issue access token → generate and persist a refresh token.
 */
@Service
public class AuthenticationService implements AuthenticateUseCase {

	private final UserRepositoryPort userRepository;
	private final PasswordHasherPort passwordHasher;
	private final AccessTokenIssuerPort accessTokenIssuer;
	private final LoginThrottlePort loginThrottle;
	private final SecretTokenGeneratorPort refreshTokenGenerator;
	private final RefreshTokenRepositoryPort refreshTokenRepository;

	public AuthenticationService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher,
			AccessTokenIssuerPort accessTokenIssuer, LoginThrottlePort loginThrottle,
			SecretTokenGeneratorPort refreshTokenGenerator, RefreshTokenRepositoryPort refreshTokenRepository) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.accessTokenIssuer = accessTokenIssuer;
		this.loginThrottle = loginThrottle;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Override
	@Transactional
	public LoginResult login(LoginCommand command) {
		String throttleKey = throttleKey(command.username(), command.clientIp());
		loginThrottle.checkAllowed(throttleKey);

		UserAccount user = userRepository.findByUsername(command.username()).orElse(null);
		if (user == null || !passwordHasher.matches(command.password(), user.passwordHash())) {
			loginThrottle.recordFailure(throttleKey);
			throw new InvalidCredentialsException();
		}
		if (!user.active()) {
			loginThrottle.recordFailure(throttleKey);
			throw new UserDisabledException();
		}

		loginThrottle.recordSuccess(throttleKey);

		AuthenticatedPrincipal principal = new AuthenticatedPrincipal(user.externalId(), user.username(), user.role(),
				user.branchExternalId());
		AccessTokenIssuerPort.IssuedAccessToken accessToken = accessTokenIssuer.issue(principal);

		String rawRefreshToken = refreshTokenGenerator.generate();
		refreshTokenRepository.persist(
				new RefreshTokenRepositoryPort.NewRefreshToken(user.externalId(), UUID.randomUUID(), rawRefreshToken));

		return new LoginResult(accessToken.token(), accessToken.expiresInSeconds(), rawRefreshToken,
				user.role().name(), user.branchExternalId());
	}

	private static String throttleKey(String username, String clientIp) {
		return username.toLowerCase(Locale.ROOT) + "|" + clientIp;
	}
}
