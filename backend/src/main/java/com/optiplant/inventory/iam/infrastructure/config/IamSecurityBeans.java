package com.optiplant.inventory.iam.infrastructure.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Hosts the two beans {@code SecurityConfig}'s {@code oauth2ResourceServer(...).jwt(...)}
 * DSL needs: the HMAC decoder (types resolved and verified in slice 1, see design's
 * {@code SecurityConfig} section) and {@link IamPrincipalConverter}.
 */
@Configuration
class IamSecurityBeans {

	@Bean
	JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
		SecretKey secretKey = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	IamPrincipalConverter iamPrincipalConverter() {
		return new IamPrincipalConverter();
	}
}
