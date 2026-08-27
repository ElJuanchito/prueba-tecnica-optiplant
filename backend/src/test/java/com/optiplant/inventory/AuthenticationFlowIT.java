package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.iam.infrastructure.adapter.out.persistence.UserJpaEntity;
import com.optiplant.inventory.iam.infrastructure.adapter.out.persistence.UserSpringDataRepository;
import java.util.UUID;
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
