package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase;
import com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException;
import com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException.Reason;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Product catalog spec against a real PostgreSQL 17 (Testcontainers): the full
 * create (with and without inline units) / edit / disable / enable / read-by-{@code
 * externalId} cycle, {@code 409 duplicate_sku} on both create and edit,
 * {@code 409 category_inactive} on create under an inactive category and on
 * re-enabling under one, {@code 404 category_not_found}, an inactive product still
 * answering {@code 200} with {@code active: false} (R-10), the {@code PUT}
 * rejection of a {@code baseUnit} field (design §6.1, D-8), the listing filters /
 * {@code size} clamp / {@code sort} allow-list (R-12), the seeded {@code npk}
 * search hit, that a {@code Pageable} {@code Sort} built from {@code ProductSort}
 * actually orders on all three fields both directions (task 5.4), that
 * disabling a product with stock leaves its {@code branch_inventories} rows
 * untouched (R-10), and the {@code PATCH .../base-unit} endpoint (DT-07, paid):
 * success against a stock-free product, {@code 409 base_unit_has_history} against
 * one with balances.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductCatalogIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SEED_ACTIVE_CATEGORY = UUID.fromString("c0000000-0000-0000-0000-000000000001");
	private static final UUID SEED_NPK_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	// admin.corp's real external_id (02-seed-data.sql) — AuditWritePort resolves userId to a real
	// `users` row, so a fabricated UUID.randomUUID() fails with "No user found" (tasks.md 3.7).
	private static final UUID SEED_ADMIN_USER = UUID.fromString("e0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ManageProductsUseCase manageProductsUseCase;

	private RestClient restClient;
	private String adminToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = tokenFor("admin.corp", SEED_PASSWORD);
	}

	// --- 5.9 ---------------------------------------------------------------

	@Test
	void fullCreateEditDisableEnableReadCycle() {
		String sfx = suffix();
		ProductDetailBody withUnits = createOk(new CreateBody("PROD-UNITS-" + sfx, "Producto con unidades " + sfx,
				"desc", SEED_ACTIVE_CATEGORY, "KG",
				List.of(new UnitBody("SACO_" + sfx, new BigDecimal("50.0000"), true)))).getBody();
		assertThat(withUnits).isNotNull();
		assertThat(withUnits.externalId()).isNotNull();
		assertThat(withUnits.active()).isTrue();
		assertThat(withUnits.baseUnit()).isEqualTo("KG");
		assertThat(withUnits.category()).isNotNull();
		assertThat(withUnits.category().externalId()).isEqualTo(SEED_ACTIVE_CATEGORY);
		assertThat(withUnits.units()).hasSize(1);
		assertThat(withUnits.units().get(0).unitName()).isEqualTo("SACO_" + sfx.toUpperCase(java.util.Locale.ROOT));
		assertThat(withUnits.units().get(0).defaultSaleUnit()).isTrue();

		ResponseEntity<ProductDetailBody> created = createOk(new CreateBody("PROD-BARE-" + sfx, "Producto sin unidades "
				+ sfx, null, SEED_ACTIVE_CATEGORY, "LITRO", null));
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		ProductDetailBody bare = created.getBody();
		assertThat(bare).isNotNull();
		assertThat(bare.units()).isEmpty();
		assertThat(created.getHeaders().getLocation()).isNotNull();
		assertThat(created.getHeaders().getLocation().toString())
				.isEqualTo("/api/catalog/products/" + bare.externalId());

		ProductDetailBody fetched = get(bare.externalId());
		assertThat(fetched.externalId()).isEqualTo(bare.externalId());

		ProductDetailBody edited = edit(bare.externalId(), new EditBody("PROD-BARE-EDIT-" + sfx, "Renombrado " + sfx,
				"nueva desc", SEED_ACTIVE_CATEGORY, null));
		assertThat(edited.sku()).isEqualTo("PROD-BARE-EDIT-" + sfx.toUpperCase(java.util.Locale.ROOT));
		assertThat(edited.name()).isEqualTo("Renombrado " + sfx);
		assertThat(edited.description()).isEqualTo("nueva desc");
		assertThat(edited.externalId()).isEqualTo(bare.externalId());

		ProductDetailBody disabled = patch(bare.externalId(), "disable");
		assertThat(disabled.active()).isFalse();

		ProductDetailBody stillReadable = get(bare.externalId());
		assertThat(stillReadable.active()).isFalse();

		ProductDetailBody enabled = patch(bare.externalId(), "enable");
		assertThat(enabled.active()).isTrue();
	}

	@Test
	void duplicateSkuIsRejectedOnCreateAndOnEdit() {
		String sfx = suffix();
		String sku = "DUP-SKU-" + sfx;
		createOk(new CreateBody(sku, "Primero " + sfx, null, SEED_ACTIVE_CATEGORY, "KG", null));

		ResponseEntity<ErrorBody> onCreate = createRaw(new CreateBody(sku.toLowerCase(java.util.Locale.ROOT),
				"Segundo " + sfx, null, SEED_ACTIVE_CATEGORY, "KG", null));
		assertThat(onCreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(onCreate.getBody()).isNotNull();
		assertThat(onCreate.getBody().code()).isEqualTo("duplicate_sku");

		ProductDetailBody other = createOk(new CreateBody("OTHER-SKU-" + sfx, "Otro " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		assertThat(other).isNotNull();

		ResponseEntity<ErrorBody> onEdit = editRaw(other.externalId(), new EditBody(sku, "Otro " + sfx, null,
				SEED_ACTIVE_CATEGORY, null));
		assertThat(onEdit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(onEdit.getBody()).isNotNull();
		assertThat(onEdit.getBody().code()).isEqualTo("duplicate_sku");
	}

	@Test
	void categoryInactiveIsRejectedOnCreateAndOnReEnable() {
		String sfx = suffix();
		UUID inactiveCategory = createCategory("IT Cat Inactiva " + sfx);
		patchCategory(inactiveCategory, "disable");

		ResponseEntity<ErrorBody> onCreate = createRaw(new CreateBody("PROD-INACT-CAT-" + sfx, "P " + sfx, null,
				inactiveCategory, "KG", null));
		assertThat(onCreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(onCreate.getBody()).isNotNull();
		assertThat(onCreate.getBody().code()).isEqualTo("category_inactive");

		UUID category = createCategory("IT Cat ReEnable " + sfx);
		ProductDetailBody product = createOk(new CreateBody("PROD-REENABLE-" + sfx, "P " + sfx, null, category,
				"KG", null)).getBody();
		assertThat(product).isNotNull();
		patch(product.externalId(), "disable");
		patchCategory(category, "disable");

		ResponseEntity<ErrorBody> onEnable = patchRaw(product.externalId(), "enable");
		assertThat(onEnable.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(onEnable.getBody()).isNotNull();
		assertThat(onEnable.getBody().code()).isEqualTo("category_inactive");
	}

	@Test
	void unknownCategoryReturns404OnCreate() {
		ResponseEntity<ErrorBody> response = createRaw(new CreateBody("PROD-NO-CAT-" + suffix(), "P", null,
				UUID.randomUUID(), "KG", null));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("category_not_found");
	}

	// --- 5.10 --------------------------------------------------------------

	@Test
	void putCarryingABaseUnitFieldReturns400AndChangesNothing() {
		String sfx = suffix();
		ProductDetailBody product = createOk(new CreateBody("PROD-BU-" + sfx, "Base unit " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		assertThat(product).isNotNull();

		ResponseEntity<ErrorBody> response = restClient.put().uri("/api/catalog/products/{id}", product.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new EditWithBaseUnitBody("PROD-BU-" + sfx, "Intento de cambio " + sfx, null,
						SEED_ACTIVE_CATEGORY, "LITRO"))
				.retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_request");

		ProductDetailBody after = get(product.externalId());
		assertThat(after.baseUnit()).isEqualTo("KG");
		assertThat(after.name()).isEqualTo("Base unit " + sfx);
	}

	// --- 5.11 --------------------------------------------------------------

	@Test
	void listingRespectsActiveFilterSizeClampAndSortAllowList() {
		String sfx = suffix();
		ProductDetailBody active = createOk(new CreateBody("LIST-ACT-" + sfx, "Activo " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		ProductDetailBody inactive = createOk(new CreateBody("LIST-INACT-" + sfx, "Inactivo " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		assertThat(active).isNotNull();
		assertThat(inactive).isNotNull();
		patch(inactive.externalId(), "disable");

		List<UUID> byDefault = listExternalIds("?q=" + sfx + "&size=100");
		assertThat(byDefault).contains(active.externalId()).doesNotContain(inactive.externalId());

		List<UUID> onlyInactive = listExternalIds("?q=" + sfx + "&active=false&size=100");
		assertThat(onlyInactive).contains(inactive.externalId()).doesNotContain(active.externalId());

		List<UUID> all = listExternalIds("?q=" + sfx + "&active=all&size=100");
		assertThat(all).contains(active.externalId(), inactive.externalId());

		ResponseEntity<ErrorBody> maybe = restClient.get().uri("/api/catalog/products?active=maybe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
		assertThat(maybe.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(maybe.getBody()).isNotNull();
		assertThat(maybe.getBody().code()).isEqualTo("invalid_request");

		@SuppressWarnings("unchecked")
		Map<String, Object> clamped = restClient.get().uri("/api/catalog/products?size=5000")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(Map.class);
		assertThat(clamped).isNotNull();
		assertThat(((Number) clamped.get("size")).intValue()).isEqualTo(100);

		ResponseEntity<ErrorBody> injected = restClient.get()
				.uri(b -> b.path("/api/catalog/products").queryParam("sort", "(select 1)").build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
		assertThat(injected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(injected.getBody()).isNotNull();
		assertThat(injected.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void searchFindsTheSeededNpkProduct() {
		List<UUID> hits = listExternalIds("?q=npk&size=100");
		assertThat(hits).contains(SEED_NPK_PRODUCT);
	}

	// --- 5.4 (BLOCKING) --------------------------------------------------

	@Test
	void pageableSortFromProductSortOrdersAllThreeFieldsInBothDirections() throws InterruptedException {
		String sfx = suffix();
		// created 1st: sku MMM, name Zzz
		UUID first = createOk(new CreateBody("MMM-SORT-" + sfx, "Prod Zzz " + sfx, null, SEED_ACTIVE_CATEGORY,
				"KG", null)).getBody().externalId();
		Thread.sleep(20);
		// created 2nd: sku AAA, name Mmm
		UUID second = createOk(new CreateBody("AAA-SORT-" + sfx, "Prod Mmm " + sfx, null, SEED_ACTIVE_CATEGORY,
				"KG", null)).getBody().externalId();
		Thread.sleep(20);
		// created 3rd: sku ZZZ, name Aaa
		UUID third = createOk(new CreateBody("ZZZ-SORT-" + sfx, "Prod Aaa " + sfx, null, SEED_ACTIVE_CATEGORY,
				"KG", null)).getBody().externalId();

		List<UUID> mine = List.of(first, second, third);

		assertThat(orderedSubset("?q=" + sfx + "&size=100&sort=sku&direction=asc", mine))
				.containsExactly(second, first, third);
		assertThat(orderedSubset("?q=" + sfx + "&size=100&sort=sku&direction=desc", mine))
				.containsExactly(third, first, second);

		assertThat(orderedSubset("?q=" + sfx + "&size=100&sort=name&direction=asc", mine))
				.containsExactly(third, second, first);
		assertThat(orderedSubset("?q=" + sfx + "&size=100&sort=name&direction=desc", mine))
				.containsExactly(first, second, third);

		assertThat(orderedSubset("?q=" + sfx + "&size=100&sort=createdAt&direction=asc", mine))
				.containsExactly(first, second, third);
		assertThat(orderedSubset("?q=" + sfx + "&size=100&sort=createdAt&direction=desc", mine))
				.containsExactly(third, second, first);
	}

	// --- 5.12 --------------------------------------------------------------

	@Test
	void disablingAProductWithStockInTwoBranchesLeavesBranchInventoriesUntouched() {
		String sfx = suffix();
		ProductDetailBody product = createOk(new CreateBody("STOCK-" + sfx, "Con stock " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		assertThat(product).isNotNull();

		Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE external_id = ?", Long.class,
				product.externalId());
		jdbcTemplate.update("INSERT INTO branch_inventories (branch_id, product_id, current_stock) VALUES (1, ?, ?)",
				productId, new BigDecimal("25.5000"));
		jdbcTemplate.update("INSERT INTO branch_inventories (branch_id, product_id, current_stock) VALUES (2, ?, ?)",
				productId, new BigDecimal("10.0000"));

		ProductDetailBody disabled = patch(product.externalId(), "disable");
		assertThat(disabled.active()).isFalse();

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT branch_id, current_stock FROM branch_inventories WHERE product_id = ? ORDER BY branch_id",
				productId);
		assertThat(rows).hasSize(2);
		assertThat(((Number) rows.get(0).get("branch_id")).longValue()).isEqualTo(1L);
		assertThat((BigDecimal) rows.get(0).get("current_stock")).isEqualByComparingTo("25.5000");
		assertThat(((Number) rows.get(1).get("branch_id")).longValue()).isEqualTo(2L);
		assertThat((BigDecimal) rows.get(1).get("current_stock")).isEqualByComparingTo("10.0000");
	}

	// --- 3.7 / DT-07 (base-unit change, exposed end to end) -------------------------------

	/**
	 * DT-07, paid: {@code PATCH /products/{externalId}/base-unit} now reaches
	 * {@code ManageProductsUseCase.changeBaseUnit} through real HTTP, exercising the full stack —
	 * {@code SecurityConfig}'s {@code ADMIN} matcher, the controller, the service's single
	 * transaction and the real {@code InventoryStockPresenceAdapter} — for a product with no stock
	 * and no Kardex history, where the change must succeed.
	 */
	@Test
	void changeBaseUnitEndpointSucceedsForAnUntouchedProduct() {
		String sfx = suffix();
		ProductDetailBody product = createOk(new CreateBody("BASEUNIT-OK-" + sfx, "Sin stock " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		assertThat(product).isNotNull();

		ProductDetailBody updated = changeBaseUnit(product.externalId(), "LITRO");

		assertThat(updated.baseUnit()).isEqualTo("LITRO");
		assertThat(get(product.externalId()).baseUnit()).isEqualTo("LITRO");
	}

	/**
	 * Opposite branch of {@code InventoryStockPresenceAdapter}'s predicate: a product with a
	 * non-zero {@code branch_inventories} balance is refused with {@code 409
	 * base_unit_has_history} (DT-07 plan step 3) — the adapter answering for real must not
	 * accidentally fail open — and {@code base_unit} is left untouched.
	 */
	@Test
	void changeBaseUnitEndpointRejectsAProductWithBranchInventoryHistory() {
		String sfx = suffix();
		ProductDetailBody product = createOk(new CreateBody("BASEUNIT-HIST-" + sfx, "Con stock " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		assertThat(product).isNotNull();
		Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE external_id = ?", Long.class,
				product.externalId());
		jdbcTemplate.update("INSERT INTO branch_inventories (branch_id, product_id, current_stock) VALUES (1, ?, ?)",
				productId, new BigDecimal("5.0000"));

		ResponseEntity<ErrorBody> response = changeBaseUnitRaw(product.externalId(), "LITRO");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("base_unit_has_history");
		assertThat(get(product.externalId()).baseUnit()).isEqualTo("KG");
	}

	/**
	 * Direct-bean regression kept alongside the HTTP test above: it pins
	 * {@code BaseUnitChangeRejectedException.reason()} to {@code HAS_HISTORY}, a domain detail the
	 * HTTP error code intentionally does not re-expose one-to-one (contract §7 exposes only the
	 * {@code code} string).
	 */
	@Test
	void changeBaseUnitServiceThrowsHasHistoryReasonForAProductWithBranchInventoryHistory() {
		String sfx = suffix();
		ProductDetailBody product = createOk(new CreateBody("BASEUNIT-REASON-" + sfx, "Con stock " + sfx, null,
				SEED_ACTIVE_CATEGORY, "KG", null)).getBody();
		assertThat(product).isNotNull();
		Long productId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE external_id = ?", Long.class,
				product.externalId());
		jdbcTemplate.update("INSERT INTO branch_inventories (branch_id, product_id, current_stock) VALUES (1, ?, ?)",
				productId, new BigDecimal("5.0000"));
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(SEED_ADMIN_USER, "admin.corp", Role.ADMIN, null);

		assertThatThrownBy(() -> manageProductsUseCase.changeBaseUnit(admin, product.externalId(), "LITRO"))
				.isInstanceOfSatisfying(BaseUnitChangeRejectedException.class,
						ex -> assertThat(ex.reason()).isEqualTo(Reason.HAS_HISTORY));
	}

	// --- helpers ---------------------------------------------------------

	private ResponseEntity<ProductDetailBody> createOk(CreateBody body) {
		return restClient.post().uri("/api/catalog/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().toEntity(ProductDetailBody.class);
	}

	private ResponseEntity<ErrorBody> createRaw(CreateBody body) {
		return restClient.post().uri("/api/catalog/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
	}

	private ProductDetailBody get(UUID externalId) {
		return restClient.get().uri("/api/catalog/products/{id}", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(ProductDetailBody.class);
	}

	private ProductDetailBody edit(UUID externalId, EditBody body) {
		return restClient.put().uri("/api/catalog/products/{id}", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().body(ProductDetailBody.class);
	}

	private ResponseEntity<ErrorBody> editRaw(UUID externalId, EditBody body) {
		return restClient.put().uri("/api/catalog/products/{id}", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
	}

	private ProductDetailBody patch(UUID externalId, String action) {
		return restClient.method(HttpMethod.PATCH).uri("/api/catalog/products/{id}/{action}", externalId, action)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(ProductDetailBody.class);
	}

	private ResponseEntity<ErrorBody> patchRaw(UUID externalId, String action) {
		return restClient.method(HttpMethod.PATCH).uri("/api/catalog/products/{id}/{action}", externalId, action)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve()
				.onStatus(status -> true, (req, res) -> {}).toEntity(ErrorBody.class);
	}

	private ProductDetailBody changeBaseUnit(UUID externalId, String newBaseUnit) {
		return restClient.method(HttpMethod.PATCH).uri("/api/catalog/products/{id}/base-unit", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new ChangeBaseUnitBody(newBaseUnit)).retrieve().body(ProductDetailBody.class);
	}

	private ResponseEntity<ErrorBody> changeBaseUnitRaw(UUID externalId, String newBaseUnit) {
		return restClient.method(HttpMethod.PATCH).uri("/api/catalog/products/{id}/base-unit", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(new ChangeBaseUnitBody(newBaseUnit)).retrieve().onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
	}

	private UUID createCategory(String name) {
		@SuppressWarnings("unchecked")
		Map<String, Object> body = restClient.post().uri("/api/catalog/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("name", name)).retrieve().body(Map.class);
		return UUID.fromString(body.get("externalId").toString());
	}

	private void patchCategory(UUID externalId, String action) {
		restClient.method(HttpMethod.PATCH).uri("/api/catalog/categories/{id}/{action}", externalId, action)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().toBodilessEntity();
	}

	private List<UUID> listExternalIds(String query) {
		@SuppressWarnings("unchecked")
		Map<String, Object> page = restClient.get().uri("/api/catalog/products" + query)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
		return content.stream().map(entry -> UUID.fromString(entry.get("externalId").toString())).toList();
	}

	private List<UUID> orderedSubset(String query, List<UUID> keep) {
		return listExternalIds(query).stream().filter(keep::contains).toList();
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

	private record CreateBody(String sku, String name, String description, UUID categoryExternalId, String baseUnit,
			List<UnitBody> units) {
	}

	private record EditBody(String sku, String name, String description, UUID categoryExternalId, String baseUnit) {
	}

	private record ChangeBaseUnitBody(String baseUnit) {
	}

	private record EditWithBaseUnitBody(String sku, String name, String description, UUID categoryExternalId,
			String baseUnit) {
	}

	private record UnitBody(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {
	}

	private record CategoryRefBody(UUID externalId, String name, boolean active) {
	}

	private record ProductDetailBody(UUID externalId, String sku, String name, String description, String baseUnit,
			boolean active, CategoryRefBody category, List<UnitBody> units, String createdAt, String updatedAt) {
	}

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId, String branchName, String branchCode) {
	}
}
