package com.optiplant.inventory.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase.CategoryQuery;
import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase.CreateCategoryCommand;
import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase.EditCategoryCommand;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort;
import com.optiplant.inventory.catalog.domain.exception.CategoryInUseException;
import com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException;
import com.optiplant.inventory.catalog.domain.exception.DuplicateCategoryNameException;
import com.optiplant.inventory.catalog.domain.model.ActiveFilter;
import com.optiplant.inventory.catalog.domain.model.Category;
import com.optiplant.inventory.catalog.domain.model.CategoryName;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.CategorySummary;
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
 * Unit tests for {@link CategoryAdminService} using hand-written in-memory fakes
 * (no Mockito on classpath, mirroring {@code iam}'s {@code BranchAdminServiceTest}).
 * Covers R-02 (duplicate name), R-03 (idempotent lifecycle), R-04 (disable
 * blocked by an active product) and R-15 (audit on every mutation).
 */
class CategoryAdminServiceTest {

	private FakeCategoryRepositoryPort categoryRepository;
	private FakeAuditWritePort auditWritePort;
	private CategoryAdminService service;
	private AuthenticatedPrincipal admin;

	@BeforeEach
	void setUp() {
		categoryRepository = new FakeCategoryRepositoryPort();
		auditWritePort = new FakeAuditWritePort();
		service = new CategoryAdminService(categoryRepository, auditWritePort);
		admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN, null);
	}

	@Test
	void createsACategoryWithATrimmedName() {
		CategorySummary created = service.create(admin, new CreateCategoryCommand("  Fertilizantes  ", "Abonos"));

		assertThat(created.category().externalId()).isNotNull();
		assertThat(created.category().name().value()).isEqualTo("Fertilizantes");
		assertThat(created.category().active()).isTrue();
	}

	@Test
	void rejectsACaseInsensitiveDuplicateNameOnCreateWithA409PathException() {
		categoryRepository.seed(activeCategory("Fertilizantes"));

		assertThatThrownBy(() -> service.create(admin, new CreateCategoryCommand("  fertilizantes  ", null)))
				.isInstanceOf(DuplicateCategoryNameException.class);
		assertThat(auditWritePort.recorded).isEmpty();
	}

	@Test
	void rejectsABlankNameBeforeReachingTheDomain() {
		assertThatThrownBy(() -> service.create(admin, new CreateCategoryCommand("   ", null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void editingACategoryToItsOwnNameIsNotAConflict() {
		Category existing = categoryRepository.seed(activeCategory("Fertilizantes"));

		CategorySummary updated = service.edit(admin, existing.externalId(),
				new EditCategoryCommand("Fertilizantes", "Nueva descripción"));

		assertThat(updated.category().description()).isEqualTo("Nueva descripción");
	}

	@Test
	void editingAnUnknownExternalIdThrowsCategoryNotFound() {
		assertThatThrownBy(() -> service.edit(admin, UUID.randomUUID(), new EditCategoryCommand("X", null)))
				.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void disableIsBlockedByAnActiveProduct() {
		Category existing = categoryRepository.seed(activeCategory("Fertilizantes"));
		categoryRepository.setHasActiveProducts(true);

		assertThatThrownBy(() -> service.disable(admin, existing.externalId()))
				.isInstanceOf(CategoryInUseException.class);

		assertThat(categoryRepository.byExternalId.get(existing.externalId()).active()).isTrue();
		assertThat(auditWritePort.recorded).isEmpty();
	}

	@Test
	void disableIsAllowedWhenOnlyInactiveProductsRemain() {
		Category existing = categoryRepository.seed(activeCategory("Fertilizantes"));
		categoryRepository.setHasActiveProducts(false);

		CategorySummary disabled = service.disable(admin, existing.externalId());

		assertThat(disabled.category().active()).isFalse();
		assertThat(auditWritePort.recorded).hasSize(1);
		assertThat(auditWritePort.recorded.get(0).action()).isEqualTo("DISABLE");
	}

	@Test
	void doubleDisableIsIdempotent() {
		Category existing = categoryRepository.seed(activeCategory("Fertilizantes"));

		service.disable(admin, existing.externalId());
		CategorySummary secondCall = service.disable(admin, existing.externalId());

		assertThat(secondCall.category().active()).isFalse();
		assertThat(auditWritePort.recorded).hasSize(1); // the no-op second call writes no audit entry
	}

	@Test
	void disablingAnUnknownExternalIdThrowsCategoryNotFound() {
		assertThatThrownBy(() -> service.disable(admin, UUID.randomUUID()))
				.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void doubleEnableIsIdempotent() {
		Category existing = categoryRepository.seed(inactiveCategory("Fertilizantes"));

		CategorySummary firstCall = service.enable(admin, existing.externalId());
		CategorySummary secondCall = service.enable(admin, existing.externalId());

		assertThat(firstCall.category().active()).isTrue();
		assertThat(secondCall.category().active()).isTrue();
		assertThat(auditWritePort.recorded).hasSize(1);
		assertThat(auditWritePort.recorded.get(0).action()).isEqualTo("ENABLE");
	}

	@Test
	void everyMutationWritesAnAuditEntryForTheCategoriesEntityWithNoBranch() {
		CategorySummary created = service.create(admin, new CreateCategoryCommand("Fertilizantes", "Abonos"));
		UUID id = created.category().externalId();

		service.edit(admin, id, new EditCategoryCommand("Fertilizantes y Nutrición", "Abonos"));
		service.disable(admin, id);
		service.enable(admin, id);

		assertThat(auditWritePort.recorded).extracting(AuditEntryCommand::action)
				.containsExactly("CREATE", "UPDATE", "DISABLE", "ENABLE");
		assertThat(auditWritePort.recorded).allSatisfy(entry -> {
			assertThat(entry.entityName()).isEqualTo("categories");
			assertThat(entry.branchId()).isNull();
			assertThat(entry.actorUserId()).isEqualTo(admin.userId());
			assertThat(entry.entityId()).isEqualTo(id.toString());
		});
		assertThat(auditWritePort.recorded.get(0).payloadBefore()).isNull();
		assertThat(auditWritePort.recorded.get(0).payloadAfter()).contains("\"name\":\"Fertilizantes\"");
		assertThat(auditWritePort.recorded.get(2).payloadBefore()).contains("\"active\":true");
		assertThat(auditWritePort.recorded.get(2).payloadAfter()).contains("\"active\":false");
	}

	@Test
	void getReturnsTheStoredSummaryAndThrowsWhenAbsent() {
		Category existing = categoryRepository.seed(activeCategory("Fertilizantes"));

		assertThat(service.get(existing.externalId()).category().externalId()).isEqualTo(existing.externalId());
		assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void listDelegatesTheFilterToThePort() {
		categoryRepository.seed(activeCategory("Fertilizantes"));
		categoryRepository.seed(inactiveCategory("Semillas"));

		assertThat(service.list(query(ActiveFilter.ACTIVE)).content()).hasSize(1);
		assertThat(service.list(query(ActiveFilter.INACTIVE)).content()).hasSize(1);
		assertThat(service.list(query(ActiveFilter.ALL)).content()).hasSize(2);
	}

	private static CategoryQuery query(ActiveFilter active) {
		return new CategoryQuery(null, active, 0, 20);
	}

	private static Category activeCategory(String name) {
		Instant now = Instant.now();
		return new Category(UUID.randomUUID(), new CategoryName(name), null, true, now, now);
	}

	private static Category inactiveCategory(String name) {
		Instant now = Instant.now();
		return new Category(UUID.randomUUID(), new CategoryName(name), null, false, now, now);
	}

	private static final class FakeCategoryRepositoryPort implements CategoryRepositoryPort {

		private final Map<UUID, Category> byExternalId = new HashMap<>();
		private boolean hasActiveProducts;
		private long activeProductCount;

		Category seed(Category category) {
			byExternalId.put(category.externalId(), category);
			return category;
		}

		void setHasActiveProducts(boolean value) {
			this.hasActiveProducts = value;
		}

		@Override
		public Optional<CategorySummary> findByExternalId(UUID externalId) {
			return Optional.ofNullable(byExternalId.get(externalId))
					.map(category -> new CategorySummary(category, activeProductCount));
		}

		@Override
		public Optional<CategoryRef> findRefByExternalId(UUID externalId) {
			return Optional.ofNullable(byExternalId.get(externalId))
					.map(category -> new CategoryRef(category.externalId(), category.name().value(), category.active()));
		}

		@Override
		public boolean existsByNameIgnoringCase(String comparisonKey, UUID excludingExternalId) {
			return byExternalId.values().stream()
					.filter(category -> excludingExternalId == null || !category.externalId().equals(excludingExternalId))
					.anyMatch(category -> category.name().comparisonKey().equals(comparisonKey));
		}

		@Override
		public boolean hasActiveProducts(UUID externalId) {
			return hasActiveProducts;
		}

		@Override
		public CategorySummary create(NewCategory newCategory) {
			Instant now = Instant.now();
			Category category = new Category(UUID.randomUUID(), new CategoryName(newCategory.name()),
					newCategory.description(), true, now, now);
			byExternalId.put(category.externalId(), category);
			return new CategorySummary(category, 0);
		}

		@Override
		public CategorySummary update(UUID externalId, CategoryUpdate update) {
			Category updated = byExternalId.get(externalId)
					.withName(new CategoryName(update.name()), update.description(), update.updatedAt());
			byExternalId.put(externalId, updated);
			return new CategorySummary(updated, activeProductCount);
		}

		@Override
		public CategorySummary setActive(UUID externalId, boolean active, Instant updatedAt) {
			Category updated = byExternalId.get(externalId).withActive(active, updatedAt);
			byExternalId.put(externalId, updated);
			return new CategorySummary(updated, activeProductCount);
		}

		@Override
		public CategoryPage list(CategoryFilter filter) {
			List<CategorySummary> content = byExternalId.values().stream()
					.filter(category -> switch (filter.active()) {
						case ACTIVE -> category.active();
						case INACTIVE -> !category.active();
						case ALL -> true;
					})
					.map(category -> new CategorySummary(category, activeProductCount))
					.toList();
			return new CategoryPage(content, content.size(), filter.page(), filter.size());
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
