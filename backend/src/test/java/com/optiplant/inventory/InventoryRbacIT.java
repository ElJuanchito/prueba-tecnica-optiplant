package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * The contract §5 authorization matrix for {@code inventory}/{@code notifications} against a real
 * PostgreSQL 17 (Testcontainers): {@code OPERATOR} is denied adjustments, the threshold write, the
 * Kardex and the alert centre, but allowed write-offs (R-13); a corporate {@code ADMIN} gets
 * {@code 403 branch_context_required} — never a generic {@code 403} — on both a session-scoped
 * mutation and a session-scoped read (contract §5's footnote, PA-02) (tasks.md 3.6).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryRbacIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	// RIEGO-MANG-16MM — shared read-only/tiny-decrement usage with InventoryBranchIsolationIT; every
	// mutation here reads the live balance first, so accumulated tiny decrements never matter.
	private static final UUID SEED_RIEGO_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000005");

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
	void operatorIsDeniedEveryMutationAndReadExceptWriteOffsAndOwnStock() {
		String token = token("operador.bogota");

		assertThat(status(HttpMethod.POST, "/api/inventory/adjustments", token,
				Map.of("productExternalId", SEED_RIEGO_PRODUCT, "countedQuantity", "10.0000", "reason", "x")))
				.as("R-10: OPERATOR denied adjustments").isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(status(HttpMethod.PUT, "/api/inventory/stock/" + SEED_RIEGO_PRODUCT + "/threshold", token,
				Map.of("minStockThreshold", "5.0000"))).as("OPERATOR denied the threshold write (contract §5)")
				.isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(status(HttpMethod.GET, "/api/inventory/kardex", token, null))
				.as("PA-01: OPERATOR denied the Kardex").isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(status(HttpMethod.GET, "/api/notifications/alerts", token, null))
				.as("PA-01: OPERATOR denied the alert centre").isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(status(HttpMethod.PATCH, "/api/notifications/alerts/" + UUID.randomUUID() + "/resolve", token,
				null)).as("PA-01: OPERATOR denied alert resolution").isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(status(HttpMethod.GET, "/api/inventory/stock", token, null))
				.as("R-01: OPERATOR reads their own branch's stock").isEqualTo(HttpStatus.OK);

		assertThat(status(HttpMethod.GET, "/api/inventory/stock/" + SEED_RIEGO_PRODUCT + "/network", token, null))
				.as("R-03: network availability is open to every role").isEqualTo(HttpStatus.OK);

		BigDecimal currentStock = rawStock();
		assertThat(currentStock).as("nothing to write off").isGreaterThan(BigDecimal.ZERO);
		assertThat(status(HttpMethod.POST, "/api/inventory/write-offs", token,
				Map.of("productExternalId", SEED_RIEGO_PRODUCT, "quantity", "0.0001", "reason", "R-13 probe")))
				.as("R-13: OPERATOR allowed write-offs").isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void corporateAdminGetsBranchContextRequiredNeverAGenericForbidden() {
		String token = token("admin.corp");

		ResponseEntity<ErrorBody> onMutation = restClient.post().uri("/api/inventory/write-offs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("productExternalId", SEED_RIEGO_PRODUCT, "quantity", "0.0001", "reason", "PA-02 probe"))
				.retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);
		assertThat(onMutation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(onMutation.getBody()).isNotNull();
		assertThat(onMutation.getBody().code()).as("PA-02: a corporate ADMIN mutation is branch_context_required")
				.isEqualTo("branch_context_required");

		ResponseEntity<ErrorBody> onRead = restClient.get().uri("/api/inventory/stock")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve()
				.onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);
		assertThat(onRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(onRead.getBody()).isNotNull();
		assertThat(onRead.getBody().code())
				.as("contract §5: own-branch stock read has no branch for a corporate ADMIN either")
				.isEqualTo("branch_context_required");
	}

	// --- helpers -------------------------------------------------------

	private BigDecimal rawStock() {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, BRANCH_BOGOTA, SEED_RIEGO_PRODUCT);
	}

	private HttpStatus status(HttpMethod method, String path, String token, Object body) {
		RestClient.RequestBodySpec spec = restClient.method(method).uri(path)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return HttpStatus.valueOf(spec.retrieve().onStatus(s -> true, (req, res) -> {
		}).toBodilessEntity().getStatusCode().value());
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
