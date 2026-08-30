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
						// Inventario y notificaciones (add-inventory-module design §6.2). Los
						// write-offs (mermas/daños) son la única mutación abierta a OPERATOR
						// (R-13); la lectura de stock propio es de cualquier rol autenticado; el
						// resto de mutaciones y el Kardex/centro de alertas quedan en
						// ADMIN/BRANCH_MANAGER (§5). String literals únicamente: importar un
						// tipo de inventory aquí crearía la arista iam -> inventory y rompería
						// ModuleBoundariesTest.
						.requestMatchers("/api/inventory/write-offs")
						.hasAnyAuthority("ADMIN", "BRANCH_MANAGER", "OPERATOR")
						.requestMatchers(HttpMethod.GET, "/api/inventory/stock/**").authenticated()
						.requestMatchers("/api/inventory/kardex").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						.requestMatchers("/api/notifications/**").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						.requestMatchers("/api/inventory/**").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						// Transferencias y logística (add-transfers-module design §6.4). Los tres
						// matchers específicos de aprobación/rechazo/cancelación van ANTES del
						// general /api/transfers/** — R-06/R-21 exigen ADMIN/BRANCH_MANAGER, y si
						// el general fuera primero OPERATOR alcanzaría la aprobación (se evalúan
						// de arriba abajo, mismo motivo que el corte de catálogo más arriba).
						// Solicitud, despacho y recepción quedan abiertos a cualquier rol
						// autenticado — RF-TRA-01/03/04/05 no los restringen — la sucursal actuante
						// y OPERATOR se validan más adentro (TransferAccessPolicy). Rutas de
						// logística son ADMIN exclusivo (CU-LOG-01); monitor y reporte quedan en
						// ADMIN/BRANCH_MANAGER (CU-LOG-02/03). String literals únicamente: importar
						// un tipo de transfers/logistics aquí crearía la arista iam -> transfers (o
						// -> logistics) y rompería ModuleBoundariesTest. hasAuthority, nunca hasRole.
						.requestMatchers("/api/transfers/*/approval", "/api/transfers/*/rejection",
								"/api/transfers/*/cancellation")
						.hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						.requestMatchers("/api/transfers/**").authenticated()
						.requestMatchers("/api/logistics/routes/**").hasAuthority("ADMIN")
						.requestMatchers("/api/logistics/**").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						// Precios y ventas (add-sales-module design §6.4). Los matchers de lectura
						// y cotización van ANTES de la regla general de administración de precios
						// (PA-03: todo vendedor necesita consultar precios vigentes); la anulación
						// de ventas exige ADMIN/BRANCH_MANAGER y va ANTES de la regla general de
						// ventas (R-22). String literals únicamente para evitar iam -> pricing/sales.
						.requestMatchers(HttpMethod.POST, "/api/pricing/quotes").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/pricing/**").authenticated()
						.requestMatchers("/api/pricing/**").hasAuthority("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/sales/customers", "/api/sales/customers/**")
						.authenticated()
						.requestMatchers("/api/sales/customers", "/api/sales/customers/**")
						.hasAuthority("ADMIN")
						.requestMatchers("/api/sales/*/cancellation").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
						.requestMatchers("/api/sales/**").authenticated()
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
