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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

/**
 * Cross-cutting authorization spec for the whole {@code /api/catalog/**} surface
 * against a real PostgreSQL 17 (Testcontainers) — S8 tasks 8.1–8.4.
 *
 * <ul>
 *   <li><b>8.1</b> {@code OPERATOR} and {@code BRANCH_MANAGER} get {@code 403} on
 *       every §6 mutation endpoint and {@code 200} on every read endpoint; an
 *       {@code ADMIN} sees byte-for-byte what an {@code OPERATOR} sees on reads
 *       (R-01, R-16).</li>
 *   <li><b>8.2</b> no token and an expired token each yield {@code 401} on every
 *       endpoint of the module (R-01).</li>
 *   <li><b>8.3</b> a corporate {@code ADMIN} with {@code branch_id = NULL} can
 *       perform every mutation — no rule in this module requires a branch on the
 *       principal (contract §5 note 2).</li>
 *   <li><b>8.4</b> two users from different branches read the same product and get
 *       byte-identical representations (R-16).</li>
 * </ul>
 *
 * <p>The {@code 403} assertions deliberately target <em>seeded</em> ids: the
 * role-based rejection resolves in the filter chain before any controller runs
 * (design §7, contract §5 note 3), so the endpoint is exercised without the
 * request ever mutating anything.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogRbacIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SEED_CATEGORY = UUID.fromString("c0000000-0000-0000-0000-000000000001");
	private static final UUID SEED_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID SEED_UNIT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JwtProperties jwtProperties;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	// --- endpoint catalogue -------------------------------------------------

	private record Endpoint(HttpMethod method, String path, boolean mutation, Object body) {
	}

	private List<Endpoint> mutationEndpoints() {
		Map<String, Object> categoryBody = Map.of("name", "RBAC Probe " + suffix(), "description", "x");
		Map<String, Object> productBody = Map.of("sku", "RBAC-" + suffix(), "name", "RBAC Probe",
				"categoryExternalId", SEED_CATEGORY.toString(), "baseUnit", "KG");
		Map<String, Object> editProductBody = Map.of("sku", "RBAC-" + suffix(), "name", "RBAC Probe",
				"categoryExternalId", SEED_CATEGORY.toString());
		Map<String, Object> unitBody = Map.of("unitName", "RBACUNIT", "conversionFactor", 2, "defaultSaleUnit", false);
		return List.of(
				new Endpoint(HttpMethod.POST, "/api/catalog/categories", true, categoryBody),
				new Endpoint(HttpMethod.PUT, "/api/catalog/categories/" + SEED_CATEGORY, true, categoryBody),
				new Endpoint(HttpMethod.PATCH, "/api/catalog/categories/" + SEED_CATEGORY + "/disable", true, null),
				new Endpoint(HttpMethod.PATCH, "/api/catalog/categories/" + SEED_CATEGORY + "/enable", true, null),
				new Endpoint(HttpMethod.POST, "/api/catalog/products", true, productBody),
				new Endpoint(HttpMethod.PUT, "/api/catalog/products/" + SEED_PRODUCT, true, editProductBody),
				new Endpoint(HttpMethod.PATCH, "/api/catalog/products/" + SEED_PRODUCT + "/disable", true, null),
				new Endpoint(HttpMethod.PATCH, "/api/catalog/products/" + SEED_PRODUCT + "/enable", true, null),
				new Endpoint(HttpMethod.POST, "/api/catalog/products/" + SEED_PRODUCT + "/units", true, unitBody),
				new Endpoint(HttpMethod.PUT, "/api/catalog/products/" + SEED_PRODUCT + "/units/" + SEED_UNIT, true,
						unitBody),
				new Endpoint(HttpMethod.DELETE, "/api/catalog/products/" + SEED_PRODUCT + "/units/" + SEED_UNIT, true,
						null));
	}

	private List<Endpoint> readEndpoints() {
		return List.of(
				new Endpoint(HttpMethod.GET, "/api/catalog/categories", false, null),
				new Endpoint(HttpMethod.GET, "/api/catalog/categories/" + SEED_CATEGORY, false, null),
				new Endpoint(HttpMethod.GET, "/api/catalog/products", false, null),
				new Endpoint(HttpMethod.GET, "/api/catalog/products/" + SEED_PRODUCT, false, null),
				new Endpoint(HttpMethod.GET, "/api/catalog/products/" + SEED_PRODUCT + "/units", false, null));
	}

	// --- 8.1 -------------------------------------------------------------

	@Test
	void operatorAndBranchManagerGet403OnEveryMutationAnd200OnEveryRead() {
		for (String username : List.of("operador.bogota", "gerente.bogota")) {
			String token = token(username);

			for (Endpoint endpoint : mutationEndpoints()) {
				ResponseEntity<String> response = call(endpoint, token);
				assertThat(response.getStatusCode())
						.as("%s %s as %s must be 403", endpoint.method(), endpoint.path(), username)
						.isEqualTo(HttpStatus.FORBIDDEN);
			}

			for (Endpoint endpoint : readEndpoints()) {
				ResponseEntity<String> response = call(endpoint, token);
				assertThat(response.getStatusCode())
						.as("%s %s as %s must be 200", endpoint.method(), endpoint.path(), username)
						.isEqualTo(HttpStatus.OK);
			}
		}
	}

	@Test
	void adminSeesExactlyWhatAnOperatorSeesOnEveryReadEndpoint() {
		String adminToken = token("admin.corp");
		String operatorToken = token("operador.bogota");

		for (Endpoint endpoint : readEndpoints()) {
			String adminBody = call(endpoint, adminToken).getBody();
			String operatorBody = call(endpoint, operatorToken).getBody();
			assertThat(operatorBody)
					.as("read %s must be identical for ADMIN and OPERATOR (R-16)", endpoint.path())
					.isEqualTo(adminBody);
		}
	}

	// --- 8.2 -------------------------------------------------------------

	@Test
	void noTokenAndExpiredTokenBothYield401OnEveryEndpoint() {
		String expired = mintExpiredAdminToken();

		List<Endpoint> all = concat(mutationEndpoints(), readEndpoints());
		for (Endpoint endpoint : all) {
			assertThat(call(endpoint, null).getStatusCode())
					.as("%s %s with no token must be 401", endpoint.method(), endpoint.path())
					.isEqualTo(HttpStatus.UNAUTHORIZED);
			assertThat(call(endpoint, expired).getStatusCode())
					.as("%s %s with an expired token must be 401", endpoint.method(), endpoint.path())
					.isEqualTo(HttpStatus.UNAUTHORIZED);
		}
	}

	// --- 8.3 -------------------------------------------------------------

	@Test
	void corporateAdminWithNullBranchCanPerformEveryMutation() {
		String token = token("admin.corp");
		String sfx = suffix();

		// category create + edit
		UUID category = UUID.fromString(post("/api/catalog/categories", token,
				Map.of("name", "Corp Admin Cat " + sfx, "description", "d")).get("externalId").toString());
		put("/api/catalog/categories/" + category, token,
				Map.of("name", "Corp Admin Cat " + sfx + " ed", "description", "d2"));

		// product create + edit under that category
		UUID product = UUID.fromString(post("/api/catalog/products", token,
				Map.of("sku", "CORP-ADMIN-" + sfx, "name", "Corp Admin Prod " + sfx,
						"categoryExternalId", category.toString(), "baseUnit", "KG")).get("externalId").toString());
		put("/api/catalog/products/" + product, token,
				Map.of("sku", "CORP-ADMIN-" + sfx + "-ED", "name", "Corp Admin Prod " + sfx,
						"categoryExternalId", category.toString()));

		// units: add, replace, delete
		UUID unit = UUID.fromString(post("/api/catalog/products/" + product + "/units", token,
				Map.of("unitName", "CAJA", "conversionFactor", 12, "defaultSaleUnit", true)).get("externalId")
				.toString());
		put("/api/catalog/products/" + product + "/units/" + unit, token,
				Map.of("unitName", "CAJA", "conversionFactor", 24, "defaultSaleUnit", true));
		assertThat(status(HttpMethod.DELETE, "/api/catalog/products/" + product + "/units/" + unit, token, null))
				.isEqualTo(HttpStatus.NO_CONTENT);

		// lifecycle patches on both resources
		assertThat(status(HttpMethod.PATCH, "/api/catalog/products/" + product + "/disable", token, null))
				.isEqualTo(HttpStatus.OK);
		assertThat(status(HttpMethod.PATCH, "/api/catalog/products/" + product + "/enable", token, null))
				.isEqualTo(HttpStatus.OK);
		// disable the product first so the category has no active product blocking R-04
		status(HttpMethod.PATCH, "/api/catalog/products/" + product + "/disable", token, null);
		assertThat(status(HttpMethod.PATCH, "/api/catalog/categories/" + category + "/disable", token, null))
				.isEqualTo(HttpStatus.OK);
		assertThat(status(HttpMethod.PATCH, "/api/catalog/categories/" + category + "/enable", token, null))
				.isEqualTo(HttpStatus.OK);
	}

	// --- 8.4 -------------------------------------------------------------

	@Test
	void twoUsersFromDifferentBranchesReadTheSameProductByteForByte() {
		String bogota = token("operador.bogota");
		String medellin = token("operador.medellin");

		String detailBogota = get("/api/catalog/products/" + SEED_PRODUCT, bogota);
		String detailMedellin = get("/api/catalog/products/" + SEED_PRODUCT, medellin);
		assertThat(detailMedellin).isEqualTo(detailBogota);

		String unitsBogota = get("/api/catalog/products/" + SEED_PRODUCT + "/units", bogota);
		String unitsMedellin = get("/api/catalog/products/" + SEED_PRODUCT + "/units", medellin);
		assertThat(unitsMedellin).isEqualTo(unitsBogota);
	}

	// --- helpers -------------------------------------------------------

	private static <T> List<T> concat(List<T> a, List<T> b) {
		return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
	}

	private ResponseEntity<String> call(Endpoint endpoint, String token) {
		RestClient.RequestBodySpec spec = restClient.method(endpoint.method()).uri(endpoint.path());
		if (token != null) {
			spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (endpoint.body() != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON);
			spec = spec.body(endpoint.body());
		}
		return spec.retrieve().onStatus(s -> true, (req, res) -> {
		}).toEntity(String.class);
	}

	private org.springframework.http.HttpStatusCode status(HttpMethod method, String path, String token, Object body) {
		return call(new Endpoint(method, path, true, body), token).getStatusCode();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> post(String path, String token, Object body) {
		return restClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> put(String path, String token, Object body) {
		return restClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
	}

	private String get(String path, String token) {
		return restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve()
				.body(String.class);
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	/** An access token signed with the running app's secret whose {@code exp} claim
	 * already lies in the past — {@code NimbusJwtDecoder} rejects on the claim value,
	 * no sleep needed (mirrors {@code AuthenticationFlowIT.mintAccessToken}). */
	private String mintExpiredAdminToken() {
		Instant expired = Instant.now().minus(Duration.ofMinutes(1));
		Instant issued = expired.minus(Duration.ofMinutes(15));
		SecretKey key = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuedAt(issued)
				.expiresAt(expired)
				.subject(UUID.randomUUID().toString())
				.claims(m -> {
					m.put("role", "ADMIN");
					m.put("branch_id", null);
					m.put("username", "expired.admin");
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
