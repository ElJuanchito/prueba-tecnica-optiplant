package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.iam.infrastructure.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpMethod;
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
 * Cross-cutting audit spec for {@code catalog} against a real PostgreSQL 17
 * (Testcontainers) — S8 task 8.5, modelled on {@code AuditAtomicityIT}.
 *
 * <ul>
 *   <li>every mutation across the three resources (categories, products,
 *       product_units) writes an {@code audit_logs} row with the actor, the entity
 *       name, the affected {@code external_id}, before/after payloads and
 *       {@code branch_id = NULL} (R-15, R-16);</li>
 *   <li>a failing audit write rolls the catalog mutation back — the
 *       atomic-effects invariant (CLAUDE.md): the audit port is synchronous and
 *       joins the caller's transaction, so if it throws, nothing is persisted.</li>
 * </ul>
 *
 * <p>The rollback half makes the audit write fail <em>naturally</em>: a bearer
 * token minted for a {@code sub} (user {@code external_id}) that no {@code users}
 * row carries. The request clears the security chain (the chain never hits the DB),
 * the catalog row is inserted, then {@code AuditWriteAdapter.requireUserId} cannot
 * resolve the actor and throws — the same-transaction write fails and the whole
 * mutation is undone.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogAuditIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SEED_ACTIVE_CATEGORY = UUID.fromString("c0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtProperties jwtProperties;

	private RestClient restClient;
	private String adminToken;
	private long adminUserId;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = token("admin.corp");
		adminUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'admin.corp'", Long.class);
	}

	// --- 8.5 positive: every mutation leaves a trail ----------------------

	@Test
	void everyMutationAcrossTheThreeResourcesWritesAnAuditRowWithNullBranch() {
		String sfx = suffix();

		// --- categories: create / edit / disable / enable ---
		UUID category = UUID.fromString(post("/api/catalog/categories",
				Map.of("name", "Audit Cat " + sfx, "description", "d")).get("externalId").toString());
		assertCreateRow("categories", category);

		put("/api/catalog/categories/" + category, Map.of("name", "Audit Cat " + sfx + " ed", "description", "d2"));
		assertUpdateRow("categories", category, "UPDATE");

		// give the category an inactive product so R-04 does not block the disable
		Long categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE external_id = ?", Long.class,
				category);
		jdbcTemplate.update(
				"INSERT INTO products (external_id, category_id, sku, name, base_unit, is_active) "
						+ "VALUES (?, ?, ?, ?, 'KG', FALSE)",
				UUID.randomUUID(), categoryId, "AUDIT-CAT-INACT-" + sfx, "inactivo");

		patch("/api/catalog/categories/" + category + "/disable");
		assertUpdateRow("categories", category, "DISABLE");
		patch("/api/catalog/categories/" + category + "/enable");
		assertUpdateRow("categories", category, "ENABLE");

		// --- products: create / edit / disable / enable ---
		UUID product = UUID.fromString(post("/api/catalog/products",
				Map.of("sku", "AUDIT-PROD-" + sfx, "name", "Audit Prod " + sfx,
						"categoryExternalId", SEED_ACTIVE_CATEGORY.toString(), "baseUnit", "KG"))
				.get("externalId").toString());
		assertCreateRow("products", product);

		put("/api/catalog/products/" + product, Map.of("sku", "AUDIT-PROD-" + sfx + "-ED", "name", "Audit Prod " + sfx,
				"categoryExternalId", SEED_ACTIVE_CATEGORY.toString()));
		assertUpdateRow("products", product, "UPDATE");
		patch("/api/catalog/products/" + product + "/disable");
		assertUpdateRow("products", product, "DISABLE");
		patch("/api/catalog/products/" + product + "/enable");
		assertUpdateRow("products", product, "ENABLE");

		// --- product_units: add / replace / delete ---
		UUID unit = UUID.fromString(post("/api/catalog/products/" + product + "/units",
				Map.of("unitName", "CAJA", "conversionFactor", 12, "defaultSaleUnit", true)).get("externalId")
				.toString());
		assertCreateRow("product_units", unit);

		put("/api/catalog/products/" + product + "/units/" + unit,
				Map.of("unitName", "CAJA", "conversionFactor", 24, "defaultSaleUnit", true));
		assertUpdateRow("product_units", unit, "UPDATE");

		assertThat(restClient.method(HttpMethod.DELETE)
				.uri("/api/catalog/products/" + product + "/units/" + unit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(204);
		// DELETE: payload_before present, payload_after null, branch_id null
		Map<String, Object> deleteRow = singleAuditRow("product_units", unit, "DELETE");
		assertThat(deleteRow.get("user_id")).isEqualTo(adminUserId);
		assertThat(deleteRow.get("branch_id")).isNull();
		assertThat(deleteRow.get("payload_before")).isNotNull();
		assertThat(deleteRow.get("payload_after")).isNull();
	}

	// --- 8.5 negative: a failing audit write rolls the mutation back ------

	@Test
	void aFailingAuditWriteRollsTheCatalogMutationBack() {
		String name = "Audit Rollback " + suffix();
		String orphanActorToken = mintAdminTokenForUnknownUser();

		Integer auditRowsBefore = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE entity_name = 'categories'", Integer.class);

		ResponseEntity<String> response = restClient.post().uri("/api/catalog/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + orphanActorToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("name", name, "description", "should not survive"))
				.retrieve().onStatus(s -> true, (req, res) -> {
				}).toEntity(String.class);

		assertThat(response.getStatusCode().is5xxServerError())
				.as("the audit write fails, so the request must not succeed")
				.isTrue();

		Integer categoryRows = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM categories WHERE name = ?", Integer.class, name);
		assertThat(categoryRows).as("the category insert must have rolled back with the failed audit write").isZero();

		Integer auditRowsAfter = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE entity_name = 'categories'", Integer.class);
		assertThat(auditRowsAfter).as("no audit row may survive the rollback").isEqualTo(auditRowsBefore);
	}

	// --- helpers ---------------------------------------------------------

	private void assertCreateRow(String entityName, UUID externalId) {
		Map<String, Object> row = singleAuditRow(entityName, externalId, "CREATE");
		assertThat(row.get("user_id")).as("actor").isEqualTo(adminUserId);
		assertThat(row.get("branch_id")).as("catalog is corporate — branch_id null (R-16)").isNull();
		assertThat(row.get("payload_before")).as("create has no before state").isNull();
		assertThat(row.get("payload_after")).as("create records the new state").isNotNull();
	}

	private void assertUpdateRow(String entityName, UUID externalId, String action) {
		Map<String, Object> row = singleAuditRow(entityName, externalId, action);
		assertThat(row.get("user_id")).as("actor").isEqualTo(adminUserId);
		assertThat(row.get("branch_id")).as("catalog is corporate — branch_id null (R-16)").isNull();
		assertThat(row.get("payload_before")).as("%s records the prior state", action).isNotNull();
		assertThat(row.get("payload_after")).as("%s records the resulting state", action).isNotNull();
	}

	private Map<String, Object> singleAuditRow(String entityName, UUID externalId, String action) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT user_id, branch_id, payload_before, payload_after FROM audit_logs "
						+ "WHERE entity_name = ? AND entity_id = ? AND action = ?",
				entityName, externalId.toString(), action);
		assertThat(rows).as("exactly one %s audit row for %s %s", action, entityName, externalId).hasSize(1);
		return rows.get(0);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> post(String path, Object body) {
		return restClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> put(String path, Object body) {
		return restClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
	}

	private void patch(String path) {
		restClient.method(HttpMethod.PATCH).uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve().toBodilessEntity();
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	/** A valid, unexpired ADMIN token whose {@code sub} is a random UUID no
	 * {@code users} row carries. Authorization passes (the chain never queries the
	 * DB); the audit write then cannot resolve the actor and throws. */
	private String mintAdminTokenForUnknownUser() {
		Instant now = Instant.now();
		SecretKey key = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofMinutes(15)))
				.subject(UUID.randomUUID().toString())
				.claims(m -> {
					m.put("role", "ADMIN");
					m.put("branch_id", null);
					m.put("username", "orphan.actor");
				})
				.build();
		Jwt jwt = encoder.encode(JwtEncoderParameters.from(claims));
		return jwt.getTokenValue();
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
