package com.optiplant.inventory.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase.CreateProductCommand;
import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase.EditProductCommand;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort;
import com.optiplant.inventory.catalog.domain.exception.CategoryInactiveException;
import com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException;
import com.optiplant.inventory.catalog.domain.exception.DuplicateSkuException;
import com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.CategorySummary;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.catalog.domain.model.Sku;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProductAdminService} using hand-written in-memory fakes
 * (no Mockito on classpath, mirroring {@code CategoryAdminServiceTest}). Covers
 * R-05 (category resolution: missing 404, inactive 409 on create/edit/enable),
 * R-06/R-09 (SKU uniqueness with {@code excludingExternalId} so editing to the
 * own SKU is not a conflict) and R-15 (audit on every mutation).
 */
class ProductAdminServiceTest {

	private FakeProductRepositoryPort productRepository;
	private FakeCategoryRepositoryPort categoryRepository;
	private FakeAuditWritePort auditWritePort;
	private ProductAdminService service;
	private AuthenticatedPrincipal admin;

	@BeforeEach
	void setUp() {
		productRepository = new FakeProductRepositoryPort();
		categoryRepository = new FakeCategoryRepositoryPort();
		auditWritePort = new FakeAuditWritePort();
		service = new ProductAdminService(productRepository, categoryRepository, auditWritePort);
		admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN, null);
	}

	// --- create -------------------------------------------------------------

	@Test
	void createRejectsAnUnknownCategoryWithNotFoundAndWritesNoAudit() {
		assertThatThrownBy(() -> service.create(admin, createCommand(UUID.randomUUID(), "FERT-1")))
				.isInstanceOf(CategoryNotFoundException.class);
		assertThat(auditWritePort.recorded).isEmpty();
	}

	@Test
	void createRejectsAnInactiveCategoryWithConflictAndWritesNoAudit() {
		CategoryRef inactive = categoryRepository.seed(inactiveCategory());

		assertThatThrownBy(() -> service.create(admin, createCommand(inactive.externalId(), "FERT-1")))
				.isInstanceOf(CategoryInactiveException.class);
		assertThat(auditWritePort.recorded).isEmpty();
	}

	@Test
	void createRejectsADuplicateSkuCaseInsensitivelyAndWritesNoAudit() {
		CategoryRef category = categoryRepository.seed(activeCategory());
		productRepository.seed(product("FERT-NPK-151515", true, category));

		assertThatThrownBy(() -> service.create(admin, createCommand(category.externalId(), "fert-npk-151515")))
				.isInstanceOf(DuplicateSkuException.class);
		assertThat(auditWritePort.recorded).isEmpty();
	}

	@Test
	void createPersistsAnActiveProductAndWritesACreateAudit() {
		CategoryRef category = categoryRepository.seed(activeCategory());

		Product created = service.create(admin, createCommand(category.externalId(), "  fert-npk-151515  "));

		assertThat(created.sku().value()).isEqualTo("FERT-NPK-151515");
		assertThat(created.active()).isTrue();
		assertThat(auditWritePort.recorded).hasSize(1);
		assertThat(auditWritePort.recorded.get(0).action()).isEqualTo("CREATE");
	}

	// --- edit ---------------------------------------------------------------

	@Test
	void editRejectsAnUnknownProduct() {
		assertThatThrownBy(() -> service.edit(admin, UUID.randomUUID(), editCommand(UUID.randomUUID(), "X-1")))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void editRejectsAnUnknownCategory() {
		CategoryRef category = categoryRepository.seed(activeCategory());
		Product existing = productRepository.seed(product("FERT-1", true, category));

		assertThatThrownBy(() -> service.edit(admin, existing.externalId(), editCommand(UUID.randomUUID(), "FERT-1")))
				.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void editRejectsMovingIntoAnInactiveCategory() {
		CategoryRef active = categoryRepository.seed(activeCategory());
		CategoryRef inactive = categoryRepository.seed(inactiveCategory());
		Product existing = productRepository.seed(product("FERT-1", true, active));

		assertThatThrownBy(() -> service.edit(admin, existing.externalId(), editCommand(inactive.externalId(), "FERT-1")))
				.isInstanceOf(CategoryInactiveException.class);
	}

	@Test
	void editRejectsASkuOwnedByAnotherProduct() {
		CategoryRef category = categoryRepository.seed(activeCategory());
		productRepository.seed(product("FERT-1", true, category));
		Product other = productRepository.seed(product("FERT-2", true, category));

		assertThatThrownBy(() -> service.edit(admin, other.externalId(), editCommand(category.externalId(), "fert-1")))
				.isInstanceOf(DuplicateSkuException.class);
	}

	@Test
	void editingAProductToItsOwnSkuIsNotAConflict() {
		CategoryRef category = categoryRepository.seed(activeCategory());
		Product existing = productRepository.seed(product("FERT-1", true, category));

		Product updated = service.edit(admin, existing.externalId(),
				editCommand(category.externalId(), "fert-1"));

		assertThat(updated.sku().value()).isEqualTo("FERT-1");
		assertThat(auditWritePort.recorded).extracting(AuditEntryCommand::action).containsExactly("UPDATE");
	}

	// --- enable -----------------------------------------------------------

	@Test
	void enableRejectsReEnablingUnderAnInactiveCategoryAndWritesNoAudit() {
		Product existing = productRepository.seed(product("FERT-1", false, inactiveCategory()));

		assertThatThrownBy(() -> service.enable(admin, existing.externalId()))
				.isInstanceOf(CategoryInactiveException.class);
		assertThat(auditWritePort.recorded).isEmpty();
	}

	@Test
	void enableReactivatesAProductUnderAnActiveCategory() {
		Product existing = productRepository.seed(product("FERT-1", false, activeCategory()));

		Product updated = service.enable(admin, existing.externalId());

		assertThat(updated.active()).isTrue();
		assertThat(auditWritePort.recorded).extracting(AuditEntryCommand::action).containsExactly("ENABLE");
	}

	// --- audit trail ------------------------------------------------------

	@Test
	void everyMutationWritesAnAuditEntryForTheProductsEntityWithNoBranch() {
		CategoryRef category = categoryRepository.seed(activeCategory());

		Product created = service.create(admin, createCommand(category.externalId(), "FERT-1"));
		UUID id = created.externalId();
		service.edit(admin, id, editCommand(category.externalId(), "FERT-1"));
		service.disable(admin, id);
		service.enable(admin, id);

		assertThat(auditWritePort.recorded).extracting(AuditEntryCommand::action)
				.containsExactly("CREATE", "UPDATE", "DISABLE", "ENABLE");
		assertThat(auditWritePort.recorded).allSatisfy(entry -> {
			assertThat(entry.entityName()).isEqualTo("products");
			assertThat(entry.branchId()).isNull();
			assertThat(entry.actorUserId()).isEqualTo(admin.userId());
			assertThat(entry.entityId()).isEqualTo(id.toString());
		});
		assertThat(auditWritePort.recorded.get(0).payloadBefore()).isNull();
		assertThat(auditWritePort.recorded.get(0).payloadAfter()).contains("\"sku\":\"FERT-1\"");
		assertThat(auditWritePort.recorded.get(2).payloadBefore()).contains("\"active\":true");
		assertThat(auditWritePort.recorded.get(2).payloadAfter()).contains("\"active\":false");
	}

	@Test
	void disableIsIdempotentAndWritesNoSecondAudit() {
		CategoryRef category = categoryRepository.seed(activeCategory());
		Product existing = productRepository.seed(product("FERT-1", true, category));

		service.disable(admin, existing.externalId());
		Product secondCall = service.disable(admin, existing.externalId());

		assertThat(secondCall.active()).isFalse();
		assertThat(auditWritePort.recorded).hasSize(1);
	}

	@Test
	void getReturnsTheStoredProductAndThrowsWhenAbsent() {
		CategoryRef category = categoryRepository.seed(activeCategory());
		Product existing = productRepository.seed(product("FERT-1", true, category));

		assertThat(service.get(existing.externalId()).externalId()).isEqualTo(existing.externalId());
		assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(ProductNotFoundException.class);
	}

	// --- fixtures --------------------------------------------------------

	private static CreateProductCommand createCommand(UUID categoryExternalId, String sku) {
		return new CreateProductCommand(sku, "Fertilizante Triple 15", null, categoryExternalId, "KG", List.of());
	}

	private static EditProductCommand editCommand(UUID categoryExternalId, String sku) {
		return new EditProductCommand(sku, "Fertilizante Triple 15", null, categoryExternalId);
	}

	private static CategoryRef activeCategory() {
		return new CategoryRef(UUID.randomUUID(), "Fertilizantes", true);
	}

	private static CategoryRef inactiveCategory() {
		return new CategoryRef(UUID.randomUUID(), "Fertilizantes", false);
	}

	private static Product product(String sku, boolean active, CategoryRef category) {
		Instant now = Instant.now();
		return new Product(UUID.randomUUID(), new Sku(sku), "Fertilizante Triple 15", null, UnitCode.baseUnit("KG"),
				active, category, List.of(), now, now);
	}

	private static final class FakeProductRepositoryPort implements ProductRepositoryPort {

		private final Map<UUID, Product> byExternalId = new HashMap<>();

		Product seed(Product product) {
			byExternalId.put(product.externalId(), product);
			return product;
		}

		@Override
		public Optional<Product> findByExternalId(UUID externalId) {
			return Optional.ofNullable(byExternalId.get(externalId));
		}

		@Override
		public boolean existsBySku(String normalizedSku, UUID excludingExternalId) {
			return byExternalId.values().stream()
					.filter(p -> excludingExternalId == null || !p.externalId().equals(excludingExternalId))
					.anyMatch(p -> p.sku().value().equals(normalizedSku));
		}

		@Override
		public Product create(NewProduct newProduct) {
			Instant now = Instant.now();
			List<ProductUnit> units = new ArrayList<>();
			for (NewUnitRow row : newProduct.units()) {
				units.add(new ProductUnit(UUID.randomUUID(), new UnitCode(row.unitName()), row.conversionFactor(),
						row.defaultSaleUnit(), now));
			}
			Product created = new Product(UUID.randomUUID(), new Sku(newProduct.sku()), newProduct.name(),
					newProduct.description(), UnitCode.baseUnit(newProduct.baseUnit()), true,
					new CategoryRef(newProduct.categoryExternalId(), "Fertilizantes", true), units, now, now);
			return seed(created);
		}

		@Override
		public Product update(UUID externalId, ProductUpdate update) {
			Product updated = byExternalId.get(externalId).withDetails(new Sku(update.sku()), update.name(),
					update.description(), new CategoryRef(update.categoryExternalId(), "Fertilizantes", true),
					update.updatedAt());
			byExternalId.put(externalId, updated);
			return updated;
		}

		@Override
		public Product setActive(UUID externalId, boolean active, Instant updatedAt) {
			Product updated = byExternalId.get(externalId).withActive(active, updatedAt);
			byExternalId.put(externalId, updated);
			return updated;
		}

		@Override
		public Product setBaseUnit(UUID externalId, String baseUnit, Instant updatedAt) {
			throw new UnsupportedOperationException("wired in S7");
		}

		@Override
		public ProductPage list(ProductFilter filter) {
			throw new UnsupportedOperationException("not exercised by these unit tests");
		}
	}

	private static final class FakeCategoryRepositoryPort implements CategoryRepositoryPort {

		private final Map<UUID, CategoryRef> refs = new HashMap<>();

		CategoryRef seed(CategoryRef ref) {
			refs.put(ref.externalId(), ref);
			return ref;
		}

		@Override
		public Optional<CategoryRef> findRefByExternalId(UUID externalId) {
			return Optional.ofNullable(refs.get(externalId));
		}

		@Override
		public Optional<CategorySummary> findByExternalId(UUID externalId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean existsByNameIgnoringCase(String comparisonKey, UUID excludingExternalId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasActiveProducts(UUID externalId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CategorySummary create(NewCategory newCategory) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CategorySummary update(UUID externalId, CategoryUpdate update) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CategorySummary setActive(UUID externalId, boolean active, Instant updatedAt) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CategoryPage list(CategoryFilter filter) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class FakeAuditWritePort implements AuditWritePort {

		private final List<AuditEntryCommand> recorded = new ArrayList<>();

		@Override
		public void record(AuditEntryCommand command) {
			recorded.add(command);
		}
	}
}
