package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Full branch-isolation spec (RN-08, RN-14) against a real PostgreSQL 17
 * (Testcontainers), exercised through {@code BranchIsolationFixtureController}
 * (test-source-only, see its javadoc for why no production endpoint exists yet
 * — tasks.md 3.4). Covers: cross-branch mutation {@code 403}, cross-branch read
 * {@code 200}, ADMIN mutates anywhere, and that no endpoint accepts a
 * client-supplied branch id.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BranchIsolationIT {

	private static final String SEED_PASSWORD = "Password123!";

	@LocalServerPort
	private int port;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void unOperadorMutaSuPropiaSucursal() {
		String token = accessTokenFor("operador.bogota");

		ResponseEntity<Void> respuesta = mutate(token, "bogota", null);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void unOperadorNoPuedeMutarUnaSucursalAjena() {
		String token = accessTokenFor("operador.bogota");

		ResponseEntity<Void> respuesta = mutate(token, "medellin", null);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void unGerenteDeSucursalNoPuedeMutarUnaSucursalAjena() {
		String token = accessTokenFor("gerente.bogota");

		ResponseEntity<Void> respuesta = mutate(token, "cali", null);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void unAdminCorporativoMutaCualquierSucursal() {
		String token = accessTokenFor("admin.corp");

		assertThat(mutate(token, "bogota", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(mutate(token, "medellin", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(mutate(token, "cali", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void unOperadorPuedeLeerUnaSucursalAjenaEnSoloLectura() {
		String token = accessTokenFor("operador.bogota");

		ResponseEntity<Void> respuesta = read(token, "medellin");

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void ningunEndpointAceptaUnBranchIdSuministradoPorElCliente() {
		// The fixture endpoint has no branch_id-shaped parameter at all — sending one
		// in the body changes nothing: the acting/target comparison still comes from
		// the path's fixed resource-to-branch mapping and the session's own branch
		// (RN-14 "acting branch is derived from the session only"). An operator of
		// Bogotá still gets 403 against Medellín's resource no matter what the body
		// claims.
		String token = accessTokenFor("operador.bogota");

		ResponseEntity<Void> respuesta = mutate(token, "medellin",
				Map.of("branchId", "b0000000-0000-0000-0000-000000000001"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	private ResponseEntity<Void> mutate(String accessToken, String resource, Object spoofedBody) {
		RestClient.RequestBodySpec request = restClient.patch()
				.uri("/api/test/branch-fixture/" + resource)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		if (spoofedBody != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).body(spoofedBody);
		}
		return request.retrieve().onStatus(status -> true, (req, res) -> {
		}).toBodilessEntity();
	}

	private ResponseEntity<Void> read(String accessToken, String resource) {
		return restClient.get()
				.uri("/api/test/branch-fixture/" + resource)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toBodilessEntity();
	}

	private String accessTokenFor(String username) {
		LoginResponseBody body = restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD))
				.retrieve()
				.body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
