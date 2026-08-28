package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Category catalog spec against a real PostgreSQL 17 (Testcontainers): the full
 * create/edit/list/disable/enable cycle, case-insensitive name uniqueness
 * ({@code 409 duplicate_category_name}), the active-product guard on disable
 * ({@code 409 category_in_use}), {@code 404} on an unknown {@code external_id},
 * the active-only listing default with its {@code active=false}/{@code all}
 * variants, {@code active=maybe} rejected with {@code 400}, {@code size}
 * clamped rather than rejected, and the guarantee that no numeric {@code id}
 * leaks in any body or in the {@code Location} header (§7.1 point 1).
 *
 * <p>Products cannot be created through an API yet (that is S5), so the
 * {@code category_in_use} scenario seeds a product row directly through
 * {@link JdbcTemplate}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryCatalogIT {

	private static final String SEED_PASSWORD = "Password123!";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String adminToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = tokenFor("admin.corp", SEED_PASSWORD);
	}

	@Test
	void fullCreateEditListDisableEnableCycle() {
		String name = "IT Categoria " + suffix();
		ResponseEntity<CategoryBody> createResponse = createRaw(name, "Descripción inicial");

		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CategoryBody created = createResponse.getBody();
		assertThat(created).isNotNull();
		assertThat(created.externalId()).isNotNull();
		assertThat(created.name()).isEqualTo(name);
		assertThat(created.active()).isTrue();
		assertThat(created.activeProductCount()).isZero();
		assertThat(created.createdAt()).isNotNull();
		assertThat(createResponse.getHeaders().getLocation()).isNotNull();
		assertThat(createResponse.getHeaders().getLocation().toString())
				.isEqualTo("/api/catalog/categories/" + created.externalId());

		CategoryBody fetched = get(created.externalId());
		assertThat(fetched.externalId()).isEqualTo(created.externalId());

		String renamed = name + " Editada";
		CategoryBody edited = edit(created.externalId(), renamed, "Descripción nueva");
		assertThat(edited.name()).isEqualTo(renamed);
		assertThat(edited.description()).isEqualTo("Descripción nueva");
		assertThat(edited.externalId()).isEqualTo(created.externalId());

		assertThat(listExternalIds("?active=all&size=100")).contains(created.externalId());

		CategoryBody disabled = patch(created.externalId(), "disable");
		assertThat(disabled.active()).isFalse();

		CategoryBody enabled = patch(created.externalId(), "enable");
		assertThat(enabled.active()).isTrue();
	}

	@Test
	void rejectsACaseInsensitiveDuplicateNameWith409() {
		String name = "IT Riego " + suffix();
		createRaw(name, null);

		ResponseEntity<ErrorBody> conflict = createErrorRaw(name.toLowerCase(java.util.Locale.ROOT), null);
		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflict.getBody()).isNotNull();
		assertThat(conflict.getBody().code()).isEqualTo("duplicate_category_name");
	}

	@Test
	void disableIsBlockedByAnActiveProductAndSucceedsWithOnlyInactiveOnes() {
		String name = "IT En Uso " + suffix();
		CategoryBody category = createRaw(name, null).getBody();
		assertThat(category).isNotNull();

		Long categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE external_id = ?", Long.class,
				category.externalId());
		jdbcTemplate.update(
				"INSERT INTO products (external_id, category_id, sku, name, base_unit) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), categoryId, "IT-CAT-SKU-" + suffix(), "Producto IT", "KG");

		ResponseEntity<ErrorBody> blocked = patchError(category.externalId(), "disable");
		assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(blocked.getBody()).isNotNull();
		assertThat(blocked.getBody().code()).isEqualTo("category_in_use");

		jdbcTemplate.update("UPDATE products SET is_active = FALSE WHERE category_id = ?", categoryId);

		CategoryBody disabled = patch(category.externalId(), "disable");
		assertThat(disabled.active()).isFalse();
	}

	@Test
	void unknownExternalIdReturns404OnEveryVerb() {
		UUID missing = UUID.randomUUID();

		assertThat(getStatus("/api/catalog/categories/" + missing)).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<Void> put = restClient.put().uri("/api/catalog/categories/{id}", missing)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new CategoryRequestBody("No existe", null)).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toBodilessEntity();
		assertThat(put.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		assertThat(patchError(missing, "disable").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void listingDefaultsToActiveOnlyAndHonoursTheActiveParameter() {
		String activeName = "IT Activa " + suffix();
		String inactiveName = "IT Inactiva " + suffix();
		CategoryBody active = createRaw(activeName, null).getBody();
		CategoryBody inactive = createRaw(inactiveName, null).getBody();
		assertThat(active).isNotNull();
		assertThat(inactive).isNotNull();
		patch(inactive.externalId(), "disable");

		List<UUID> defaultListing = listExternalIds("?size=100");
		assertThat(defaultListing).contains(active.externalId()).doesNotContain(inactive.externalId());

		List<UUID> inactiveListing = listExternalIds("?active=false&size=100");
		assertThat(inactiveListing).contains(inactive.externalId()).doesNotContain(active.externalId());

		List<UUID> allListing = listExternalIds("?active=all&size=100");
		assertThat(allListing).contains(active.externalId(), inactive.externalId());
	}

	@Test
	void activeMaybeIsRejectedWith400() {
		ResponseEntity<ErrorBody> response = restClient.get().uri("/api/catalog/categories?active=maybe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void anOversizedPageSizeIsClampedNotRejected() {
		@SuppressWarnings("unchecked")
		Map<String, Object> page = restClient.get().uri("/api/catalog/categories?size=5000")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(Map.class);

		assertThat(page).isNotNull();
		assertThat(((Number) page.get("size")).intValue()).isEqualTo(100);
	}

	@Test
	void noNumericIdLeaksInAnyResponseBodyOrInTheLocationHeader() {
		String name = "IT Sin Id " + suffix();
		ResponseEntity<Map> createResponse = restClient.post().uri("/api/catalog/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new CategoryRequestBody(name, "x")).retrieve().toEntity(Map.class);

		@SuppressWarnings("unchecked")
		Map<String, Object> body = createResponse.getBody();
		assertThat(body).isNotNull().doesNotContainKey("id");
		UUID externalId = UUID.fromString(body.get("externalId").toString());

		String location = createResponse.getHeaders().getLocation().toString();
		assertThat(location).isEqualTo("/api/catalog/categories/" + externalId);
		assertThat(location.replace(externalId.toString(), "")).doesNotMatch(".*\\d.*");

		@SuppressWarnings("unchecked")
		Map<String, Object> listPage = restClient.get().uri("/api/catalog/categories?active=all&size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) listPage.get("content");
		assertThat(content).isNotEmpty().allSatisfy(entry -> assertThat(entry).doesNotContainKey("id"));
	}

	// --- helpers -----------------------------------------------------------

	private ResponseEntity<CategoryBody> createRaw(String name, String description) {
		return restClient.post().uri("/api/catalog/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new CategoryRequestBody(name, description)).retrieve().toEntity(CategoryBody.class);
	}

	private ResponseEntity<ErrorBody> createErrorRaw(String name, String description) {
		return restClient.post().uri("/api/catalog/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new CategoryRequestBody(name, description)).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
	}

	private CategoryBody get(UUID externalId) {
		return restClient.get().uri("/api/catalog/categories/{id}", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(CategoryBody.class);
	}

	private HttpStatusCode getStatus(String path) {
		return restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve().onStatus(status -> true, (req, res) -> {}).toBodilessEntity().getStatusCode();
	}

	private CategoryBody edit(UUID externalId, String name, String description) {
		return restClient.put().uri("/api/catalog/categories/{id}", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new CategoryRequestBody(name, description)).retrieve().body(CategoryBody.class);
	}

	private CategoryBody patch(UUID externalId, String action) {
		return restClient.method(HttpMethod.PATCH).uri("/api/catalog/categories/{id}/{action}", externalId, action)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(CategoryBody.class);
	}

	private ResponseEntity<ErrorBody> patchError(UUID externalId, String action) {
		return restClient.method(HttpMethod.PATCH)
				.uri("/api/catalog/categories/{id}/{action}", externalId, action)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
	}

	private List<UUID> listExternalIds(String query) {
		@SuppressWarnings("unchecked")
		Map<String, Object> page = restClient.get().uri("/api/catalog/categories" + query)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
		return content.stream().map(entry -> UUID.fromString(entry.get("externalId").toString())).toList();
	}

	private String tokenFor(String username, String password) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, password)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private record CategoryRequestBody(String name, String description) {
	}

	private record CategoryBody(UUID externalId, String name, String description, boolean active,
			long activeProductCount, String createdAt, String updatedAt) {
	}

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId, String branchName, String branchCode) {
	}
}
