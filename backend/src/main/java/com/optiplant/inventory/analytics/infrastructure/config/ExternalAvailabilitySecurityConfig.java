package com.optiplant.inventory.analytics.infrastructure.config;

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
 * Dedicated {@link SecurityFilterChain} for external network availability intake (CU-EXT-01, design §7, F-6, D-11).
 *
 * <p>Annotated with {@code @Order(2)} to evaluate after {@code sales}' external chain (Order 1) and before IAM's catch-all chain.
 */
@Configuration
@EnableConfigurationProperties(ExternalAvailabilityApiKeyProperties.class)
public class ExternalAvailabilitySecurityConfig {

	@Bean
	@Order(2)
	public SecurityFilterChain externalAvailabilitySecurityFilterChain(HttpSecurity http,
			ExternalAvailabilityApiKeyFilter apiKeyFilter) throws Exception {
		return http
				.securityMatcher("/api/external/availability/**")
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
