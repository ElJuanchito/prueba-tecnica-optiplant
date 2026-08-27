package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import com.optiplant.inventory.iam.application.port.out.AccessTokenIssuerPort;
import com.optiplant.inventory.iam.infrastructure.config.JwtProperties;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues the access token: {@code sub} = user's {@code external_id}, {@code role} and
 * {@code branch_id} claims, signed HMAC-SHA256 with {@link JwtProperties#secret()} and
 * expiring after {@link JwtProperties#accessTtl()} (P1 — TTL is configuration, never a
 * code constant).
 *
 * <p>Named "OAuth2" because that is the starter that ships the JOSE/JWT toolkit used
 * here; this is a first-party token, not an OAuth2 authorization-server integration
 * (design's {@code SecurityConfig} section).
 */
@Component
public class JwtAccessTokenAdapter implements AccessTokenIssuerPort {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;

	public JwtAccessTokenAdapter(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		SecretKey secretKey = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8),
				"HmacSHA256");
		this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Override
	public IssuedAccessToken issue(AuthenticatedPrincipal principal) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperties.accessTtl());

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(principal.userId().toString())
				.claims(map -> {
					map.put("role", principal.role().name());
					// Present even for a corporate ADMIN (branchId == null): the claim's
					// absence would be ambiguous with "not yet migrated" rather than
					// "corporate", and the spec requires the claim to be carried.
					map.put("branch_id", principal.branchId() != null ? principal.branchId().toString() : null);
				})
				.build();

		Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(claims));
		return new IssuedAccessToken(jwt.getTokenValue(), jwtProperties.accessTtl().toSeconds());
	}
}
