package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.iam.infrastructure.adapter.out.persistence.UserJpaEntity;
import com.optiplant.inventory.iam.infrastructure.adapter.out.persistence.UserSpringDataRepository;
import com.optiplant.inventory.iam.infrastructure.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

/**
 * Full authentication spec (login, refresh rotation, logout) against a real
 * PostgreSQL 17 (Testcontainers).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationFlowIT {

	private static final String SEED_PASSWORD = "Password123!";
	// BCrypt hash of SEED_PASSWORD, the same value every row of
	// backend/init-db/02-seed-data.sql carries. It must stay in sync with that file: a
	// stale hash here makes every "login must fail" assertion pass for the wrong reason
	// (the password never matches), hiding whatever the test meant to prove.
	private static final String SEED_PASSWORD_HASH = "$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q";

	@LocalServerPort
	private int port;

	@Autowired
	private UserSpringDataRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtProperties jwtProperties;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void loginExitosoDeAdminCorporativoDevuelveParDeTokensSinSucursal() {
		LoginResponseBody body = login("admin.corp", SEED_PASSWORD).getBody();

		assertThat(body).isNotNull();
		assertThat(body.accessToken()).isNotBlank();
		assertThat(body.refreshToken()).isNotBlank();
		assertThat(body.expiresInSeconds()).isEqualTo(15 * 60);
		assertThat(body.role()).isEqualTo("ADMIN");
		assertThat(body.branchId()).isNull();
	}

	@Test
	void loginExitosoDeGerenteDeSucursalDevuelveSuSucursal() {
		LoginResponseBody body = login("gerente.bogota", SEED_PASSWORD).getBody();

		assertThat(body).isNotNull();
		assertThat(body.role()).isEqualTo("BRANCH_MANAGER");
		assertThat(body.branchId()).isNotNull();
	}

	@Test
	void credencialesInvalidasNoRevelanSiElUsuarioExiste() {
		ResponseEntity<String> usuarioInexistente = loginRaw("no-existe-" + UUID.randomUUID(), "cualquier-cosa");
		ResponseEntity<String> contrasenaIncorrecta = loginRaw("admin.corp", "contrasena-erronea");

		assertThat(usuarioInexistente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(contrasenaIncorrecta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(usuarioInexistente.getBody()).isEqualTo(contrasenaIncorrecta.getBody());
	}

	@Test
	void unUsuarioDeshabilitadoNoPuedeIniciarSesion() {
		String username = "it.deshabilitado." + shortSuffix();
		crearUsuarioDeshabilitado(username);

		ResponseEntity<String> respuestaDeshabilitado = loginRaw(username, SEED_PASSWORD);
		// A disabled account must be indistinguishable from bad credentials, or the
		// response itself leaks that the account exists (same no-leak posture already
		// proven for unknown-username vs. wrong-password by
		// credencialesInvalidasNoRevelanSiElUsuarioExiste). A fresh random username
		// keeps this comparison independent of any other test's login throttle state.
		ResponseEntity<String> respuestaCredencialesInvalidas = loginRaw("no-existe-" + UUID.randomUUID(),
				"cualquier-cosa");

		assertThat(respuestaDeshabilitado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(respuestaDeshabilitado.getBody()).isEqualTo(respuestaCredencialesInvalidas.getBody());
	}

	@Test
	void unUsuarioActivoConSucursalDeshabilitadaNoPuedeIniciarSesion() {
		// This user's OWN `is_active` flag stays TRUE. If it were false too, this test
		// would pass for the reason unUsuarioDeshabilitadoNoPuedeIniciarSesion already
		// covers, and would prove nothing about the branch half of UserMapper's
		// effective-active expression (entity.isActive() && (no branch || branch is
		// active)). The seed branches are never mutated here — the Testcontainers
		// container is shared across this class's tests, so flipping seed state would
		// make test execution order a hidden dependency; a dedicated branch row avoids
		// that entirely.
		String suffix = shortSuffix();
		Long branchId = jdbcTemplate.queryForObject(
				"INSERT INTO branches (code, name, address, city, is_active) VALUES (?, ?, ?, ?, false) "
						+ "RETURNING id",
				Long.class, "IT-" + suffix, "IT Disabled Branch", "Calle Falsa 123", "Bogota");

		String username = "it.sucursal." + suffix;
		UserJpaEntity user = new UserJpaEntity();
		user.setUsername(username);
		user.setEmail(username + "@optiplant.com");
		user.setPasswordHash(SEED_PASSWORD_HASH);
		user.setFullName("IT User With Disabled Branch");
		user.setRole("OPERATOR");
		user.setActive(true);
		user.setBranchId(branchId);
		userRepository.save(user);

		ResponseEntity<String> respuesta = loginRaw(username, SEED_PASSWORD);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void intentosRepetidosFallidosActivanElLimitador() {
		String username = "it.throttle." + shortSuffix();
		for (int i = 0; i < 5; i++) {
			loginRaw(username, "password-erronea");
		}

		ResponseEntity<String> ultimoIntento = loginRaw(username, "password-erronea");

		assertThat(ultimoIntento.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void loginLuegoLlamadaProtegidaLuegoRotacionDeRefreshLuegoLogout() {
		LoginResponseBody sesion = login("admin.corp", SEED_PASSWORD).getBody();
		assertThat(sesion).isNotNull();

		// Protected call: no dedicated business endpoint exists yet (slice 5), so an
		// unmapped path under authorizeHttpRequests' anyRequest().authenticated() is
		// the cheapest way to prove the bearer/decoder/converter chain actually runs
		// without adding a throwaway controller. A valid token must clear the
		// security filter chain (never 401); Spring MVC's own 404 for "no handler"
		// proves the request passed authentication.
		ResponseEntity<String> conToken = protectedProbe(sesion.accessToken());
		assertThat(conToken.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> sinToken = protectedProbe(null);
		assertThat(sinToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// Refresh rotates the refresh token (family stays, hash changes). The new
		// access token is not asserted distinct from the old one: both are minted
		// within the same second in this fast-running test, and the JWT claims
		// (sub/role/branch_id/username/iat truncated to seconds) can coincide —
		// rotation is proven by the refresh token and by the old one becoming reuse.
		RefreshResponseBody rotado = refresh(sesion.refreshToken()).getBody();
		assertThat(rotado).isNotNull();
		assertThat(rotado.refreshToken()).isNotEqualTo(sesion.refreshToken());
		assertThat(rotado.accessToken()).isNotBlank();

		// The now-rotated original refresh token is reuse: rejected.
		ResponseEntity<String> reintentoConTokenViejo = refreshRaw(sesion.refreshToken());
		assertThat(reintentoConTokenViejo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// Logout revokes the current (rotated) refresh token; requires the bearer.
		ResponseEntity<Void> respuestaLogout = logout(rotado.accessToken(), rotado.refreshToken());
		assertThat(respuestaLogout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// The now-logged-out token can no longer refresh.
		ResponseEntity<String> reintentoTrasLogout = refreshRaw(rotado.refreshToken());
		assertThat(reintentoTrasLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void unUsuarioDeshabilitadoDespuesDelLoginNoPuedeRefrescar() {
		String username = "it.baja." + shortSuffix();
		UserJpaEntity user = crearUsuarioHabilitado(username);

		LoginResponseBody sesion = login(username, SEED_PASSWORD).getBody();
		assertThat(sesion).isNotNull();

		user.setActive(false);
		userRepository.save(user);

		// Without the active check on refresh, the session would keep rotating until
		// the 7-day absolute window closed, which is exactly what revocable sessions
		// exist to prevent.
		ResponseEntity<String> respuesta = refreshRaw(sesion.refreshToken());

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refrescarConUnTokenInexistenteDevuelve401() {
		ResponseEntity<String> respuesta = refreshRaw(UUID.randomUUID().toString());

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void unTokenDeAccesoAlteradoEsRechazado() {
		LoginResponseBody sesion = login("admin.corp", SEED_PASSWORD).getBody();
		assertThat(sesion).isNotNull();

		String tokenAlterado = alterarFirma(sesion.accessToken());

		ResponseEntity<String> respuesta = protectedProbe(tokenAlterado);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void unTokenDeAccesoExpiradoEsRechazado() {
		// No sleep: mint a token whose `exp` claim already lies in the past, signed
		// with the same secret the running application is configured with, mirroring
		// exactly what JwtAccessTokenAdapter builds in production. NimbusJwtDecoder
		// rejects based on the claim's value, not on wall-clock time elapsed since
		// issuance.
		Instant expiraba = Instant.now().minus(Duration.ofMinutes(1));
		Instant fueEmitido = expiraba.minus(Duration.ofMinutes(15));
		String tokenExpirado = mintAccessToken(fueEmitido, expiraba, UUID.randomUUID().toString(), "ADMIN", null,
				"admin.corp");

		ResponseEntity<String> respuesta = protectedProbe(tokenExpirado);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void logoutEnUnDispositivoNoAfectaLaSesionDeOtro() {
		// Two independent logins as the same user create two independent refresh-token
		// families (AuthenticationService.login mints a fresh UUID family id per call).
		LoginResponseBody sesionA = login("admin.corp", SEED_PASSWORD).getBody();
		LoginResponseBody sesionB = login("admin.corp", SEED_PASSWORD).getBody();
		assertThat(sesionA).isNotNull();
		assertThat(sesionB).isNotNull();

		ResponseEntity<Void> respuestaLogout = logout(sesionA.accessToken(), sesionA.refreshToken());
		assertThat(respuestaLogout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<String> sesionATrasLogout = refreshRaw(sesionA.refreshToken());
		assertThat(sesionATrasLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// The decisive assertion: session B, from an entirely separate login, must stay
		// alive. LogoutService revokes only the presented refresh token, never the
		// whole family or the whole user (design decision P4) — a bug that revoked too
		// broadly would only be caught here, not by the assertion above.
		ResponseEntity<RefreshResponseBody> sesionBRefrescada = refresh(sesionB.refreshToken());
		assertThat(sesionBRefrescada.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(sesionBRefrescada.getBody()).isNotNull();
		assertThat(sesionBRefrescada.getBody().refreshToken()).isNotEqualTo(sesionB.refreshToken());
	}

	private ResponseEntity<String> protectedProbe(String accessToken) {
		RestClient.RequestHeadersSpec<?> request = restClient.get().uri("/api/auth/__protected-probe");
		if (accessToken != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		return request.retrieve().onStatus(status -> true, (req, res) -> {
		}).toEntity(String.class);
	}

	private ResponseEntity<RefreshResponseBody> refresh(String refreshToken) {
		return restClient.post()
				.uri("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new RefreshRequestBody(refreshToken))
				.retrieve()
				.toEntity(RefreshResponseBody.class);
	}

	private ResponseEntity<String> refreshRaw(String refreshToken) {
		return restClient.post()
				.uri("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new RefreshRequestBody(refreshToken))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);
	}

	private ResponseEntity<Void> logout(String accessToken, String refreshToken) {
		return restClient.post()
				.uri("/api/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new RefreshRequestBody(refreshToken))
				.retrieve()
				.toBodilessEntity();
	}

	/** {@code users.username} is {@code VARCHAR(50)}; a full UUID would overflow it. */
	private static String shortSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** Flips the last character of the signature segment (the third dot-separated
	 * part) so the token still parses as three well-formed Base64URL segments but
	 * fails signature verification, rather than failing to parse. */
	/** Flips the FIRST character of the signature, never the last. An HS256
	 * signature is 32 bytes, which base64url-encodes to 43 characters: the final
	 * character carries only 4 significant bits, and its 2 low bits are discarded
	 * on decode. Altering that last character therefore decodes to the very same
	 * 32 bytes roughly one time in sixteen, the token stays valid, and the test
	 * fails intermittently. Every bit of the first character is significant. */
	private static String alterarFirma(String token) {
		String[] parts = token.split("\\.");
		assertThat(parts).hasSize(3);
		String firma = parts[2];
		char primero = firma.charAt(0);
		char reemplazo = primero == 'A' ? 'B' : 'A';
		String firmaAlterada = reemplazo + firma.substring(1);
		return parts[0] + "." + parts[1] + "." + firmaAlterada;
	}

	/** Mints an access token the same way {@code JwtAccessTokenAdapter} does
	 * (NimbusJwtEncoder, HS256, same claim set), but with caller-controlled
	 * issued/expiry instants — needed to construct an already-expired token without
	 * sleeping in the test. */
	private String mintAccessToken(Instant issuedAt, Instant expiresAt, String subject, String role, String branchId,
			String username) {
		SecretKey secretKey = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8),
				"HmacSHA256");
		JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(subject)
				.claims(map -> {
					map.put("role", role);
					map.put("branch_id", branchId);
					map.put("username", username);
				})
				.build();

		Jwt jwt = encoder.encode(JwtEncoderParameters.from(claims));
		return jwt.getTokenValue();
	}

	private UserJpaEntity crearUsuarioHabilitado(String username) {
		UserJpaEntity entity = new UserJpaEntity();
		entity.setUsername(username);
		entity.setEmail(username + "@optiplant.com");
		entity.setPasswordHash(SEED_PASSWORD_HASH);
		entity.setFullName("IT Active User");
		entity.setRole("OPERATOR");
		entity.setActive(true);
		return userRepository.save(entity);
	}

	private void crearUsuarioDeshabilitado(String username) {
		UserJpaEntity entity = new UserJpaEntity();
		entity.setUsername(username);
		entity.setEmail(username + "@optiplant.com");
		entity.setPasswordHash(SEED_PASSWORD_HASH);
		entity.setFullName("IT Disabled User");
		entity.setRole("OPERATOR");
		entity.setActive(false);
		userRepository.save(entity);
	}

	private ResponseEntity<LoginResponseBody> login(String username, String password) {
		return restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, password))
				.retrieve()
				.toEntity(LoginResponseBody.class);
	}

	private ResponseEntity<String> loginRaw(String username, String password) {
		return restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, password))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId) {
	}

	private record RefreshRequestBody(String refreshToken) {
	}

	private record RefreshResponseBody(String accessToken, String refreshToken, long expiresInSeconds) {
	}
}
