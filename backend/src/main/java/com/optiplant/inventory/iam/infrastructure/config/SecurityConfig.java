package com.optiplant.inventory.iam.infrastructure.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cadena de filtros sin estado y con denegación por defecto.
 *
 * <p>Movida desde el paquete base ({@code com.optiplant.inventory}) porque ahora
 * inyecta tipos de {@code iam} (el decoder y {@code IamPrincipalConverter}) — dejarla
 * en el paquete base pondría esos imports en una clase que ninguna regla de fronteras
 * gobierna, ocultando el acoplamiento a ArchUnit (design decision "SecurityConfig and
 * JwtProperties move into iam/infrastructure/config"). Las rutas de otros módulos
 * (p. ej. {@code /api/admin/**}) llegan como cadenas literales en slices futuras, no
 * como imports, así que ninguna frontera se cruza por eso.
 *
 * <p>Declara las reglas por rol de la slice 3 con {@code hasAuthority}/{@code
 * hasAnyAuthority} — nunca {@code hasRole()}, que antepone {@code ROLE_} y la
 * restricción CHECK de {@code users} rechaza. Las rutas {@code /api/admin/**} y
 * {@code /api/audit/**} llegan recién en las slices 4-5, pero los matchers son
 * seguros de declarar ahora: ninguna ruta existe todavía bajo esos prefijos.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

	private final CorsProperties corsProperties;
	private final JwtDecoder jwtDecoder;
	private final IamPrincipalConverter iamPrincipalConverter;

	SecurityConfig(CorsProperties corsProperties, JwtDecoder jwtDecoder, IamPrincipalConverter iamPrincipalConverter) {
		this.corsProperties = corsProperties;
		this.jwtDecoder = jwtDecoder;
		this.iamPrincipalConverter = iamPrincipalConverter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				// API sin sesión: no hay cookie que proteger y CSRF rechazaría toda escritura.
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder)
						.jwtAuthenticationConverter(iamPrincipalConverter)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui", "/swagger-ui/**",
								"/swagger-ui.html")
						.permitAll()
						// Login/refresh deben ser alcanzables sin token para poder obtener uno;
						// logout exige el bearer emitido en login (design's SecurityConfig block).
						.requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
						.requestMatchers("/api/auth/logout").authenticated()
						// BRANCH_MANAGER may only manage OPERATOR users in their own branch —
						// that scoping is enforced in UserAdminService, not here.
						.requestMatchers("/api/admin/users/**").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						.requestMatchers("/api/admin/branches/**").hasAuthority("ADMIN")
						.requestMatchers("/api/audit/**").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						// Catálogo: la superficie de lectura es abierta a cualquier rol
						// autenticado, la de mutación es solo ADMIN (contract §5). El corte
						// es por método HTTP, no por ruta. El matcher GET va primero: se
						// evalúan de arriba abajo y el segundo capturaría también las
						// lecturas (design §7, D-1). hasAuthority, nunca hasRole.
						.requestMatchers(HttpMethod.GET, "/api/catalog/**").authenticated()
						.requestMatchers("/api/catalog/**").hasAuthority("ADMIN")
						.anyRequest().authenticated())
				.build();
	}

	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Sin cookies de sesión (STATELESS + bearer token), así que no hay nada que
		// las credenciales de CORS protejan; mantenerlo apagado evita ampliar la
		// superficie sin necesidad (RNF-SEC-06).
		configuration.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
