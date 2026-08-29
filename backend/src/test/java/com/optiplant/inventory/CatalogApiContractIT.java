package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Cross-cutting API-contract spec for {@code catalog} against a real PostgreSQL 17
 * (Testcontainers) — S8 tasks 8.6 and 8.7.
 *
 * <ul>
 *   <li><b>8.6</b> no internal numeric {@code id} appears in any response body or
 *       {@code Location} header across all seventeen endpoints — the sixteen of §6
 *       plus the base-unit change of DT-07 (§7.1 point 1);</li>
 *   <li><b>8.7</b> {@code /v3/api-docs} publishes all seventeen operations, the
 *       {@code { code, message }} error envelope is documented, and the base-unit
 *       route is exactly {@code PATCH .../base-unit} — no other catalog route
 *       mutates it (§6.2, DT-07, paid).</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApiContractIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SEED_ACTIVE_CATEGORY = UUID.fromString("c0000000-0000-0000-0000-000000000001");
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@LocalServerPort
	private int port;

	private RestClient restClient;
	private String adminToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = token("admin.corp");
	}

	// --- 8.6 -----------------------------------------------------------

	@Test
	void noNumericIdLeaksInAnyBodyOrLocationHeaderAcrossAllSeventeenEndpoints() throws Exception {
		String sfx = suffix();

		// categories: POST, GET list, GET one, PUT, PATCH disable, PATCH enable
		ResponseEntity<String> categoryCreated = postRaw("/api/catalog/categories",
				Map.of("name", "Contract Cat " + sfx, "description", "d"));
		UUID category = idOf(categoryCreated);
		assertNoNumericId(categoryCreated, "/api/catalog/categories/" + category);

		assertNoNumericId(getRaw("/api/catalog/categories?size=100&active=all"), null);
		assertNoNumericId(getRaw("/api/catalog/categories/" + category), null);
		assertNoNumericId(putRaw("/api/catalog/categories/" + category,
				Map.of("name", "Contract Cat " + sfx + " ed", "description", "d2")), null);
		assertNoNumericId(patchRaw("/api/catalog/categories/" + category + "/disable"), null);
		assertNoNumericId(patchRaw("/api/catalog/categories/" + category + "/enable"), null);

		// products: POST (with an inline unit), GET list, GET one, PUT, PATCH disable/enable
		ResponseEntity<String> productCreated = postRaw("/api/catalog/products",
				Map.of("sku", "CONTRACT-" + sfx, "name", "Contract Prod " + sfx,
						"categoryExternalId", SEED_ACTIVE_CATEGORY.toString(), "baseUnit", "KG",
						"units", List.of(Map.of("unitName", "SACO", "conversionFactor", 50, "defaultSaleUnit", true))));
		UUID product = idOf(productCreated);
		assertNoNumericId(productCreated, "/api/catalog/products/" + product);

		assertNoNumericId(getRaw("/api/catalog/products?size=100&active=all"), null);
		assertNoNumericId(getRaw("/api/catalog/products/" + product), null);
		assertNoNumericId(putRaw("/api/catalog/products/" + product,
				Map.of("sku", "CONTRACT-" + sfx + "-ED", "name", "Contract Prod " + sfx,
						"categoryExternalId", SEED_ACTIVE_CATEGORY.toString())), null);
		assertNoNumericId(patchRaw("/api/catalog/products/" + product + "/disable"), null);
		assertNoNumericId(patchRaw("/api/catalog/products/" + product + "/enable"), null);

		// base-unit change (DT-07): the product above has no stock and no Kardex history
		assertNoNumericId(patchBodyRaw("/api/catalog/products/" + product + "/base-unit",
				Map.of("baseUnit", "LITRO")), null);

		// units: GET list, POST, PUT, DELETE
		assertNoNumericId(getRaw("/api/catalog/products/" + product + "/units"), null);
		ResponseEntity<String> unitCreated = postRaw("/api/catalog/products/" + product + "/units",
				Map.of("unitName", "CAJA", "conversionFactor", 12, "defaultSaleUnit", false));
		UUID unit = idOf(unitCreated);
		assertNoNumericId(unitCreated, "/api/catalog/products/" + product + "/units/" + unit);
		assertNoNumericId(putRaw("/api/catalog/products/" + product + "/units/" + unit,
				Map.of("unitName", "CAJA", "conversionFactor", 24, "defaultSaleUnit", false)), null);
		ResponseEntity<String> unitDeleted = restClient.method(HttpMethod.DELETE)
				.uri("/api/catalog/products/" + product + "/units/" + unit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve().onStatus(s -> true, (req, res) -> {
				}).toEntity(String.class);
		assertThat(unitDeleted.getStatusCode().value()).isEqualTo(204);
		assertNoNumericId(unitDeleted, null);
	}

	// --- 8.7 -----------------------------------------------------------

	@Test
	void openApiPublishesTheSeventeenEndpointsAndTheErrorEnvelope() throws Exception {
		String doc = restClient.get().uri("/v3/api-docs").retrieve().body(String.class);
		JsonNode root = MAPPER.readTree(doc);
		JsonNode paths = root.get("paths");
		assertThat(paths).as("/v3/api-docs must expose a paths object").isNotNull();

		List<String> expected = List.of(
				"GET /api/catalog/categories",
				"POST /api/catalog/categories",
				"GET /api/catalog/categories/{externalId}",
				"PUT /api/catalog/categories/{externalId}",
				"PATCH /api/catalog/categories/{externalId}/disable",
				"PATCH /api/catalog/categories/{externalId}/enable",
				"GET /api/catalog/products",
				"POST /api/catalog/products",
				"GET /api/catalog/products/{externalId}",
				"PUT /api/catalog/products/{externalId}",
				"PATCH /api/catalog/products/{externalId}/disable",
				"PATCH /api/catalog/products/{externalId}/enable",
				"PATCH /api/catalog/products/{externalId}/base-unit",
				"GET /api/catalog/products/{productExternalId}/units",
				"POST /api/catalog/products/{productExternalId}/units",
				"PUT /api/catalog/products/{productExternalId}/units/{unitExternalId}",
				"DELETE /api/catalog/products/{productExternalId}/units/{unitExternalId}");

		List<String> published = new ArrayList<>();
		for (Iterator<Entry<String, JsonNode>> it = paths.fields(); it.hasNext();) {
			Entry<String, JsonNode> pathEntry = it.next();
			for (Iterator<String> verbs = pathEntry.getValue().fieldNames(); verbs.hasNext();) {
				published.add(verbs.next().toUpperCase(java.util.Locale.ROOT) + " " + pathEntry.getKey());
			}
		}

		assertThat(published).as("every §6 catalog operation plus the DT-07 base-unit route "
				+ "must be in the OpenAPI document").containsAll(expected);

		// DT-07, paid: exactly one catalog route may mutate a base unit, and it must be
		// the dedicated PATCH — never PUT/POST, which would let it slip in unannounced.
		assertThat(published.stream().filter(op -> op.contains("/api/catalog/"))
				.filter(op -> op.toLowerCase(java.util.Locale.ROOT).contains("base-unit")
						|| op.toLowerCase(java.util.Locale.ROOT).contains("baseunit"))
				.toList()).as("exactly one base-unit route, and it must be the dedicated PATCH endpoint")
				.containsExactly("PATCH /api/catalog/products/{externalId}/base-unit");

		// the { code, message } error envelope must be documented — either as a schema
		// (springdoc scans @RestControllerAdvice by default) or as a non-2xx response
		// with a body on a catalog operation.
		JsonNode schemas = root.path("components").path("schemas");
		List<String> schemaNames = new ArrayList<>();
		boolean envelopeSchemaDocumented = false;
		for (Iterator<Entry<String, JsonNode>> it = schemas.fields(); it.hasNext();) {
			Entry<String, JsonNode> schema = it.next();
			schemaNames.add(schema.getKey());
			JsonNode props = schema.getValue().get("properties");
			if (props != null && props.has("code") && props.has("message")) {
				envelopeSchemaDocumented = true;
			}
		}

		boolean nonSuccessResponseDocumented = false;
		for (Iterator<Entry<String, JsonNode>> it = paths.fields(); it.hasNext();) {
			Entry<String, JsonNode> pathEntry = it.next();
			if (!pathEntry.getKey().startsWith("/api/catalog/")) {
				continue;
			}
			for (JsonNode operation : pathEntry.getValue()) {
				JsonNode responses = operation.get("responses");
				if (responses == null) {
					continue;
				}
				for (Iterator<String> codes = responses.fieldNames(); codes.hasNext();) {
					String code = codes.next();
					if (!code.startsWith("2") && !code.equals("default")
							&& responses.get(code).has("content")) {
						nonSuccessResponseDocumented = true;
					}
				}
			}
		}

		assertThat(envelopeSchemaDocumented || nonSuccessResponseDocumented)
				.as("the { code, message } error envelope must be documented (schema or non-2xx response); "
						+ "found schemas: %s", schemaNames)
				.isTrue();
	}

	// --- helpers -----------------------------------------------------

	private void assertNoNumericId(ResponseEntity<String> response, String expectedLocationPath) throws Exception {
		String body = response.getBody();
		if (body != null && !body.isBlank()) {
			JsonNode root = MAPPER.readTree(body);
			List<String> offenders = new ArrayList<>();
			collectNumericIdOffenders(root, "$", offenders);
			assertThat(offenders).as("no internal numeric id may appear in %s", body).isEmpty();
		}
		if (expectedLocationPath != null) {
			String location = response.getHeaders().getLocation() == null ? null
					: response.getHeaders().getLocation().toString();
			assertThat(location).isEqualTo(expectedLocationPath);
			// strip every UUID, then assert nothing that looks like a bare numeric id remains
			assertThat(location.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
					"")).doesNotMatch(".*\\d.*");
		}
	}

	private static void collectNumericIdOffenders(JsonNode node, String path, List<String> offenders) {
		if (node.isObject()) {
			for (Iterator<Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
				Entry<String, JsonNode> field = it.next();
				String name = field.getKey();
				JsonNode value = field.getValue();
				// a field literally named "id" is the internal primary key — it must never cross the boundary
				if (name.equals("id")) {
					offenders.add(path + ".id");
				}
				// a *Id / *_id field that is an integer would also be an internal key leak
				// (external ids are UUID strings; counts like activeProductCount are not *Id)
				if ((name.endsWith("Id") || name.endsWith("_id")) && value.isIntegralNumber()) {
					offenders.add(path + "." + name + " = " + value.asText());
				}
				collectNumericIdOffenders(value, path + "." + name, offenders);
			}
		} else if (node.isArray()) {
			int i = 0;
			for (JsonNode element : node) {
				collectNumericIdOffenders(element, path + "[" + i++ + "]", offenders);
			}
		}
	}

	private UUID idOf(ResponseEntity<String> response) throws Exception {
		return UUID.fromString(MAPPER.readTree(response.getBody()).get("externalId").asText());
	}

	private ResponseEntity<String> getRaw(String path) {
		return restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve().onStatus(s -> true, (req, res) -> {
				}).toEntity(String.class);
	}

	private ResponseEntity<String> postRaw(String path, Object body) {
		return restClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().onStatus(s -> true, (req, res) -> {
				}).toEntity(String.class);
	}

	private ResponseEntity<String> putRaw(String path, Object body) {
		return restClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().onStatus(s -> true, (req, res) -> {
				}).toEntity(String.class);
	}

	private ResponseEntity<String> patchRaw(String path) {
		return restClient.method(HttpMethod.PATCH).uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve().onStatus(s -> true, (req, res) -> {
				}).toEntity(String.class);
	}

	private ResponseEntity<String> patchBodyRaw(String path, Object body) {
		return restClient.method(HttpMethod.PATCH).uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().onStatus(s -> true, (req, res) -> {
				}).toEntity(String.class);
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
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
