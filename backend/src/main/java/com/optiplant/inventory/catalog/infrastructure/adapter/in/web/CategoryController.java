package com.optiplant.inventory.catalog.infrastructure.adapter.in.web;

import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase;
import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase.CategoryQuery;
import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase.CreateCategoryCommand;
import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase.EditCategoryCommand;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort.CategoryPage;
import com.optiplant.inventory.catalog.domain.model.ActiveFilter;
import com.optiplant.inventory.catalog.domain.model.Category;
import com.optiplant.inventory.catalog.domain.model.CategorySummary;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/catalog/categories/**} — the six category endpoints of contract
 * §6.1. Reads are open to every authenticated role and mutations are
 * {@code ADMIN}-only; both are enforced by {@code SecurityConfig}'s
 * method-scoped {@code /api/catalog/**} matchers, not by anything here (design
 * §7, D-1). No service-level role check is needed, mirroring
 * {@code BranchAdminController}.
 *
 * <p>Every identifier exposed is an {@code external_id} UUID — no numeric id
 * appears in any response body or in the {@code Location} header (§7.1 point 1).
 * {@code POST} returns {@code 201 Created} with a {@code Location} header, a
 * deliberate deviation from {@code iam}'s plain-200 create (design §6.1).
 * {@code size} is clamped to the cap, never rejected (contract §9).
 */
@RestController
@RequestMapping("/api/catalog/categories")
public class CategoryController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageCategoriesUseCase manageCategoriesUseCase;
	private final PrincipalAccessor principalAccessor;

	public CategoryController(ManageCategoriesUseCase manageCategoriesUseCase, PrincipalAccessor principalAccessor) {
		this.manageCategoriesUseCase = manageCategoriesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@GetMapping
	public CategoryPageResponse list(@RequestParam(required = false) String name,
			@RequestParam(required = false, defaultValue = "true") String active,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		int pageNumber = Math.max(page, 0);
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		CategoryPage result = manageCategoriesUseCase
				.list(new CategoryQuery(blankToNull(name), ActiveFilter.parse(active), pageNumber, pageSize));
		List<CategoryResponse> content = result.content().stream().map(CategoryController::toResponse).toList();
		return new CategoryPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/{externalId}")
	public CategoryResponse get(@PathVariable UUID externalId) {
		return toResponse(manageCategoriesUseCase.get(externalId));
	}

	@PostMapping
	public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		CategoryResponse body = toResponse(manageCategoriesUseCase.create(actor,
				new CreateCategoryCommand(request.name(), request.description())));
		return ResponseEntity.created(URI.create("/api/catalog/categories/" + body.externalId())).body(body);
	}

	@PutMapping("/{externalId}")
	public CategoryResponse edit(@PathVariable UUID externalId, @Valid @RequestBody CategoryRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toResponse(manageCategoriesUseCase.edit(actor, externalId,
				new EditCategoryCommand(request.name(), request.description())));
	}

	@PatchMapping("/{externalId}/disable")
	public CategoryResponse disable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toResponse(manageCategoriesUseCase.disable(actor, externalId));
	}

	@PatchMapping("/{externalId}/enable")
	public CategoryResponse enable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toResponse(manageCategoriesUseCase.enable(actor, externalId));
	}

	private static String blankToNull(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static CategoryResponse toResponse(CategorySummary summary) {
		Category category = summary.category();
		return new CategoryResponse(category.externalId(), category.name().value(), category.description(),
				category.active(), summary.activeProductCount(), category.createdAt(), category.updatedAt());
	}

	public record CategoryRequest(@NotBlank @Size(max = 100) String name, String description) {
	}

	public record CategoryResponse(UUID externalId, String name, String description, boolean active,
			long activeProductCount, Instant createdAt, Instant updatedAt) {
	}

	public record CategoryPageResponse(List<CategoryResponse> content, long totalElements, int page, int size) {
	}
}
