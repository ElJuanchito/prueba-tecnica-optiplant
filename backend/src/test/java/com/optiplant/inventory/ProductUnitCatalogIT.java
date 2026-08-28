package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Units-of-measure subresource against a real PostgreSQL 17 (Testcontainers).
 *
 * <p>The two ordered tests are the proof obligation of design §8.2 for the
 * default-sale-unit write sequence of {@code ProductUnitPersistenceAdapter}: a
 * real swap on seeded product 1 must <strong>commit</strong> and leave exactly one
 * {@code is_default_sale_unit = TRUE} row, in <em>both</em> directions — the
 * ordering only has consequences when a real database checks the real partial
 * unique index {@code uq_product_units_single_default} per statement, so no unit
 * test substitutes for it. {@code @Order} keeps the pair together and restores the
 * seeded state (SACO_50KG default) for the rest of {@code ./mvnw verify}.
 *
 * <p>The remaining tests use freshly created products: two products each marking
 * their own default (the index is scoped by {@code product_id}), a product with no
 * default reading back fine, a direct second-default write surfacing as a conflict
 * rather than a {@code 500}, unit deletion touching no balance, deleting the
 * current default, a unit of another product answering {@code 404}, and a
 * {@code POST /products} carrying two inline default units answering {@code 4xx}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductUnitCatalogIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SEED_ACTIVE_CATEGORY = UUID.fromString("c0000000-0000-0000-0000-000000000001");
	private static final UUID SEED_PRODUCT_1 = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID SEED_SACO_50KG = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID SEED_BULTITO_10KG = UUID.fromString("10000000-0000-0000-0000-000000000002");

	private static final String COUNT_DEFAULTS_PRODUCT_1 =
			"SELECT count(*) FROM product_units WHERE product_id = 1 AND is_default_sale_unit";
	private static final String SURVIVING_DEFAULT_PRODUCT_1 =
			"SELECT unit_name FROM product_units WHERE product_id = 1 AND is_default_sale_unit";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String adminToken;

	private RestClient client() {
		if (restClient == null) {
			restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
			adminToken = tokenFor("admin.corp", SEED_PASSWORD);
		}
		return restClient;
	}

	// --- 6.9 (BLOCKING) --------------------------------------------------

	@Test
	@Order(1)
	void replacingTheDefaultSaleUnitCommits() {
		ResponseEntity<UnitBody> response = putUnit(SEED_PRODUCT_1, SEED_BULTITO_10KG,
				new UnitBody("BULTITO_10KG", new BigDecimal("10.0000"), true));

		// (a) committed, not aborted on uq_product_units_single_default
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		// (b) exactly one default row for product 1
		assertThat(jdbcTemplate.queryForObject(COUNT_DEFAULTS_PRODUCT_1, Long.class)).isEqualTo(1L);
		// (c) the surviving TRUE row is BULTITO_10KG
		assertThat(jdbcTemplate.queryForObject(SURVIVING_DEFAULT_PRODUCT_1, String.class)).isEqualTo("BULTITO_10KG");
	}

	// --- 6.10 ----------------------------------------------------------

	@Test
	@Order(2)
	void swappingTheDefaultBackToTheOriginalAlsoCommits() {
		ResponseEntity<UnitBody> response = putUnit(SEED_PRODUCT_1, SEED_SACO_50KG,
				new UnitBody("SACO_50KG", new BigDecimal("50.0000"), true));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(jdbcTemplate.queryForObject(COUNT_DEFAULTS_PRODUCT_1, Long.class)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(SURVIVING_DEFAULT_PRODUCT_1, String.class)).isEqualTo("SACO_50KG");
	}

	// --- 6.12 --------------------------------------------------------

	@Test
	void twoDifferentProductsCanEachMarkTheirOwnDefault() {
		String sfx = suffix();
		UUID productA = createProduct("UNIT-2P-A-" + sfx);
		UUID productB = createProduct("UNIT-2P-B-" + sfx);

		assertThat(addUnit(productA, new UnitBody("CAJA_" + sfx, new BigDecimal("12.0000"), true)).getStatusCode())
				.isEqualTo(HttpStatus.CREATED);
		assertThat(addUnit(productB, new UnitBody("CAJA_" + sfx, new BigDecimal("6.0000"), true)).getStatusCode())
				.isEqualTo(HttpStatus.CREATED);

		assertThat(defaultCountFor(productA)).isEqualTo(1L);
		assertThat(defaultCountFor(productB)).isEqualTo(1L);
	}

	@Test
	void aProductWithNoDefaultUnitReadsBackFine() {
		String sfx = suffix();
		UUID product = createProduct("UNIT-NODEF-" + sfx);
		addUnitOk(product, new UnitBody("CAJA_" + sfx, new BigDecimal("12.0000"), false));

		List<UnitResponseBody> units = listUnits(product);

		assertThat(units).hasSize(1);
		assertThat(units).allSatisfy(u -> assertThat(u.defaultSaleUnit()).isFalse());
	}

	@Test
	void aDirectSecondDefaultWriteSurfacesAsAConflictNotAServerError() {
		String sfx = suffix();
		UUID product = createProduct("UNIT-DIRECT-" + sfx);
		addUnitOk(product, new UnitBody("CAJA_" + sfx, new BigDecimal("12.0000"), true));
		Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE external_id = ?", Long.class,
				product);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO product_units (product_id, unit_name, conversion_factor, is_default_sale_unit) "
						+ "VALUES (?, ?, ?, TRUE)",
				productId, "SACO_" + sfx, new BigDecimal("50.0000")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- 6.13 --------------------------------------------------------

	@Test
	void deletingAUnitAffectsNoBalanceAndLeavesSiblingsUntouched() {
		String sfx = suffix();
		UUID product = createProduct("UNIT-DEL-BAL-" + sfx);
		UnitResponseBody keep = addUnitOk(product, new UnitBody("CAJA_" + sfx, new BigDecimal("12.0000"), false));
		UnitResponseBody drop = addUnitOk(product, new UnitBody("SACO_" + sfx, new BigDecimal("50.0000"), false));
		Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE external_id = ?", Long.class,
				product);
		jdbcTemplate.update("INSERT INTO branch_inventories (branch_id, product_id, current_stock) VALUES (1, ?, ?)",
				productId, new BigDecimal("5.0000"));

		ResponseEntity<Void> deleted = deleteUnit(product, drop.externalId());

		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(listUnits(product)).extracting(UnitResponseBody::externalId).containsExactly(keep.externalId());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM branch_inventories WHERE product_id = ?",
				Long.class, productId)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories WHERE product_id = ? AND branch_id = 1",
				BigDecimal.class, productId)).isEqualByComparingTo("5.0000");
	}

	@Test
	void deletingTheCurrentDefaultLeavesTheProductWithNone() {
		String sfx = suffix();
		UUID product = createProduct("UNIT-DEL-DEF-" + sfx);
		UnitResponseBody def = addUnitOk(product, new UnitBody("CAJA_" + sfx, new BigDecimal("12.0000"), true));

		assertThat(deleteUnit(product, def.externalId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(listUnits(product)).isEmpty();
		assertThat(defaultCountFor(product)).isEqualTo(0L);
	}

	@Test
	void aUnitIdBelongingToAnotherProductReturns404() {
		String sfx = suffix();
		UUID productA = createProduct("UNIT-404-A-" + sfx);
		UUID productB = createProduct("UNIT-404-B-" + sfx);
		UnitResponseBody unitOfA = addUnitOk(productA, new UnitBody("CAJA_" + sfx, new BigDecimal("12.0000"), false));

		ResponseEntity<ErrorBody> onDelete = deleteUnitRaw(productB, unitOfA.externalId());
		assertThat(onDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(onDelete.getBody()).isNotNull();
		assertThat(onDelete.getBody().code()).isEqualTo("product_unit_not_found");

		ResponseEntity<ErrorBody> onPut = putUnitRaw(productB, unitOfA.externalId(),
				new UnitBody("CAJA_" + sfx, new BigDecimal("24.0000"), false));
		assertThat(onPut.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(onPut.getBody()).isNotNull();
		assertThat(onPut.getBody().code()).isEqualTo("product_unit_not_found");
	}

	// --- doble-default inline payload (S5 hole, closed here) --------

	@Test
	void postingAProductWithTwoInlineDefaultUnitsIs4xxNot500() {
		String sfx = suffix();
		String sku = "UNIT-2DEF-" + sfx;

		ResponseEntity<ErrorBody> response = client().post().uri("/api/catalog/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new CreateBody(sku, "Doble default " + sfx, null, SEED_ACTIVE_CATEGORY, "KG", List.of(
						new UnitBody("CAJA_" + sfx, new BigDecimal("12.0000"), true),
						new UnitBody("SACO_" + sfx, new BigDecimal("50.0000"), true))))
				.retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);

		assertThat(response.getStatusCode().is4xxClientError()).isTrue();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_request");
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM products WHERE sku = ?", Long.class,
				sku.toUpperCase(Locale.ROOT))).isEqualTo(0L);
	}

	// --- helpers -------------------------------------------------------

	private UUID createProduct(String sku) {
		ProductBody body = client().post().uri("/api/catalog/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new CreateBody(sku, sku, null, SEED_ACTIVE_CATEGORY, "KG", null))
				.retrieve().body(ProductBody.class);
		assertThat(body).isNotNull();
		return body.externalId();
	}

	private ResponseEntity<UnitResponseBody> addUnit(UUID product, UnitBody body) {
		return client().post().uri("/api/catalog/products/{p}/units", product)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(UnitResponseBody.class);
	}

	private UnitResponseBody addUnitOk(UUID product, UnitBody body) {
		ResponseEntity<UnitResponseBody> response = addUnit(product, body);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		return response.getBody();
	}

	private ResponseEntity<UnitBody> putUnit(UUID product, UUID unit, UnitBody body) {
		return client().put().uri("/api/catalog/products/{p}/units/{u}", product, unit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(UnitBody.class);
	}

	private ResponseEntity<ErrorBody> putUnitRaw(UUID product, UUID unit, UnitBody body) {
		return client().put().uri("/api/catalog/products/{p}/units/{u}", product, unit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
	}

	private ResponseEntity<Void> deleteUnit(UUID product, UUID unit) {
		return client().method(HttpMethod.DELETE).uri("/api/catalog/products/{p}/units/{u}", product, unit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toBodilessEntity();
	}

	private ResponseEntity<ErrorBody> deleteUnitRaw(UUID product, UUID unit) {
		return client().method(HttpMethod.DELETE).uri("/api/catalog/products/{p}/units/{u}", product, unit)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
	}

	private List<UnitResponseBody> listUnits(UUID product) {
		UnitResponseBody[] units = client().get().uri("/api/catalog/products/{p}/units", product)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(UnitResponseBody[].class);
		assertThat(units).isNotNull();
		return Arrays.asList(units);
	}

	private long defaultCountFor(UUID productExternalId) {
		Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE external_id = ?", Long.class,
				productExternalId);
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM product_units WHERE product_id = ? AND is_default_sale_unit", Long.class,
				productId);
	}

	private String tokenFor(String username, String password) {
		LoginResponseBody body = RestClient.builder().baseUrl("http://localhost:" + port).build()
				.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, password)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private record CreateBody(String sku, String name, String description, UUID categoryExternalId, String baseUnit,
			List<UnitBody> units) {
	}

	private record UnitBody(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {
	}

	private record UnitResponseBody(UUID externalId, String unitName, BigDecimal conversionFactor,
			boolean defaultSaleUnit, String createdAt) {
	}

	private record ProductBody(UUID externalId, String sku, String name, String baseUnit, boolean active) {
	}

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId, String branchName, String branchCode) {
	}
}
