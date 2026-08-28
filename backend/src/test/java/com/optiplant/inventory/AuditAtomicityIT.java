package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Proves {@link com.optiplant.inventory.shared.audit.AuditWritePort} is synchronous —
 * CLAUDE.md's atomic-effects invariant, no {@code @Async}, no {@code AFTER_COMMIT}: a
 * use case that throws right after calling {@code record} must leave zero {@code
 * audit_logs} rows behind (design's "AuditAtomicityIT is the load-bearing one: it is
 * the only test that can distinguish the required synchronous port from an
 * accidental AFTER_COMMIT or @Async implementation"). Exercised through a
 * test-source-only fixture, real PostgreSQL 17 (Testcontainers) — no production use
 * case wires a mutation to the audit port yet (user/branch admin arrive in slices
 * 5a/5b).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditAtomicityIT {

	private static final String SEED_PASSWORD = "Password123!";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void unaEscrituraDeAuditoriaSeguidaDeUnaFallaNoDejaFilas() {
		String entityId = "atomicity-" + UUID.randomUUID();
		String token = accessTokenFor("admin.corp");

		ResponseEntity<String> respuesta = invoke(token, entityId, true);

		assertThat(respuesta.getStatusCode().is5xxServerError()).isTrue();
		assertThat(contarFilas(entityId)).isZero();
	}

	@Test
	void unaEscrituraDeAuditoriaSinFallaSiPersisteLaFila() {
		String entityId = "atomicity-" + UUID.randomUUID();
		String token = accessTokenFor("admin.corp");

		ResponseEntity<String> respuesta = invoke(token, entityId, false);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(contarFilas(entityId)).isEqualTo(1);
	}

	private Integer contarFilas(String entityId) {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE entity_name = 'audit-atomicity-fixture' AND entity_id = ?",
				Integer.class, entityId);
	}

	private ResponseEntity<String> invoke(String accessToken, String entityId, boolean shouldFail) {
		return restClient.post()
				.uri("/api/test/audit-atomicity/" + entityId + "?shouldFail=" + shouldFail)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);
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
