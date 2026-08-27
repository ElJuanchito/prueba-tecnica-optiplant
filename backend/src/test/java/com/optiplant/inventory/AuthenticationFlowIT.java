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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Login-only scenarios of the authentication spec against a real PostgreSQL 17
 * (Testcontainers). Slice 2b extends this same class with the refresh-rotation and
 * logout scenarios once {@code SecurityConfig} is wired for the bearer chain.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationFlowIT {

	private static final String SEED_PASSWORD = "Password123!";
	// BCrypt hash of SEED_PASSWORD, matching backend/init-db/02-seed-data.sql's header comment.
	private static final String SEED_PASSWORD_HASH = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

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

	/** {@code users.username} is {@code VARCHAR(50)}; a full UUID would overflow it. */
	private static String shortSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
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
}
