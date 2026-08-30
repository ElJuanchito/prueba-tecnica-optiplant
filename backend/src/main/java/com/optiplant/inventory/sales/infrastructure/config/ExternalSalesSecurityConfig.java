package com.optiplant.inventory.sales.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Dedicated {@link SecurityFilterChain} for the external POS intake (design §6.5, F-6, P-07).
 *
 * <p>Annotated with {@code @Order(1)} to evaluate before IAM's catch-all chain.
 */
@Configuration
@EnableConfigurationProperties(ExternalApiKeyProperties.class)
public class ExternalSalesSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain externalSalesSecurityFilterChain(HttpSecurity http,
			ExternalApiKeyAuthenticationFilter apiKeyFilter) throws Exception {
		return http
				.securityMatcher("/api/external/sales/**")
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
