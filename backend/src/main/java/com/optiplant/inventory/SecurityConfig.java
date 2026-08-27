package com.optiplant.inventory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de filtros mínima del esqueleto: sin estado y con denegación por defecto.
 *
 * <p>No declara ninguna regla por rol. El mapa de autorizaciones pertenece al módulo
 * {@code iam}, y cuando llegue usará {@code hasAuthority()} con {@code ADMIN},
 * {@code BRANCH_MANAGER} y {@code OPERATOR} — sin prefijo {@code ROLE_}, que es lo
 * que rechaza la restricción CHECK de la tabla {@code users}.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				// API sin sesión: no hay cookie que proteger y CSRF rechazaría toda escritura.
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui", "/swagger-ui/**",
								"/swagger-ui.html")
						.permitAll()
						// Todo lo demás autenticado: la denegación es el estado por defecto.
						.anyRequest().authenticated())
				.build();
	}
}
