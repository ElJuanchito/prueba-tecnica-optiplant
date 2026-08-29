package com.optiplant.inventory.catalog.infrastructure.adapter.in.web;

import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase;
import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase.CreateProductCommand;
import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase.EditProductCommand;
import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase.NewUnit;
import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase.ProductQuery;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort.ProductPage;
import com.optiplant.inventory.catalog.domain.model.ActiveFilter;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductSort;
import com.optiplant.inventory.catalog.domain.model.ProductSummary;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
 * {@code /api/catalog/products/**} — the six product endpoints of contract §6.2
 * plus the base-unit change (DT-07, paid once {@code inventory} shipped
 * {@code ProductStockPresencePort}). Reads are open to every authenticated role and
 * mutations are {@code ADMIN}-only; both are enforced by {@code SecurityConfig}'s
 * method-scoped {@code /api/catalog/**} matchers, not here (design §7, D-1),
 * mirroring {@code CategoryController}.
 *
 * <p>The detail response embeds {@code category} and {@code units}; the list item
 * omits {@code units} and {@code description} so a 100-row page cannot trigger a
 * per-row query (contract §6.2). {@code POST} returns {@code 201 Created} with a
 * {@code Location} header carrying the {@code external_id} only (§7.1 point 1).
 *
 * <p>{@code PUT} <strong>rejects</strong> a {@code baseUnit} field rather than
 * silently dropping it: {@link #changeBaseUnit} is the only path that may change it,
 * and a client that sends it here must learn the change did not happen through this
 * endpoint (design §6.1, D-8, contract §12.3 point 3). {@code "baseUnit": null} is
 * indistinguishable from absent and is treated as absent.
 *
 * <p>{@code active}, {@code sort} and {@code direction} are bound as {@code String}
 * and parsed here, never straight to an enum/{@code Boolean}: direct binding would
 * yield Spring's type-mismatch page instead of the {@code {code, message}} envelope
 * and could not express {@code all}. {@code size} is clamped to the cap, never
 * rejected (contract §9).
 */
@RestController
@RequestMapping("/api/catalog/products")
public class ProductController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageProductsUseCase manageProductsUseCase;
	private final PrincipalAccessor principalAccessor;

	public ProductController(ManageProductsUseCase manageProductsUseCase, PrincipalAccessor principalAccessor) {
		this.manageProductsUseCase = manageProductsUseCase;
		this.principalAccessor = principalAccessor;
	}

	@GetMapping
	public ProductPageResponse list(@RequestParam(required = false) String q,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false, defaultValue = "true") String active,
			@RequestParam(required = false, defaultValue = "sku") String sort,
			@RequestParam(required = false, defaultValue = "asc") String direction,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		int pageNumber = Math.max(page, 0);
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		ProductQuery query = new ProductQuery(blankToNull(q), categoryId, ActiveFilter.parse(active),
				ProductSort.parse(sort), parseAscending(direction), pageNumber, pageSize);
		ProductPage result = manageProductsUseCase.list(query);
		List<ProductListItemResponse> content = result.content().stream().map(ProductController::toListItem).toList();
		return new ProductPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/{externalId}")
	public ProductDetailResponse get(@PathVariable UUID externalId) {
		return toDetail(manageProductsUseCase.get(externalId));
	}

	@PostMapping
	public ResponseEntity<ProductDetailResponse> create(@Valid @RequestBody CreateProductRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<NewUnit> units = request.units() == null ? null
				: request.units().stream()
						.map(u -> new NewUnit(u.unitName(), u.conversionFactor(), u.defaultSaleUnit())).toList();
		Product created = manageProductsUseCase.create(actor, new CreateProductCommand(request.sku(), request.name(),
				request.description(), request.categoryExternalId(), request.baseUnit(), units));
		ProductDetailResponse body = toDetail(created);
		return ResponseEntity.created(URI.create("/api/catalog/products/" + body.externalId())).body(body);
	}

	@PutMapping("/{externalId}")
	public ProductDetailResponse edit(@PathVariable UUID externalId,
			@Valid @RequestBody EditProductRequest request) {
		if (request.baseUnit() != null) {
			throw new IllegalArgumentException("baseUnit cannot be changed through this endpoint");
		}
		AuthenticatedPrincipal actor = principalAccessor.require();
		Product updated = manageProductsUseCase.edit(actor, externalId, new EditProductCommand(request.sku(),
				request.name(), request.description(), request.categoryExternalId()));
		return toDetail(updated);
	}

	@PatchMapping("/{externalId}/disable")
	public ProductDetailResponse disable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetail(manageProductsUseCase.disable(actor, externalId));
	}

	@PatchMapping("/{externalId}/enable")
	public ProductDetailResponse enable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetail(manageProductsUseCase.enable(actor, externalId));
	}

	/**
	 * R-08 (DT-07, paid). {@code ADMIN}-only, like every other catalog mutation
	 * (contract §5, {@code SecurityConfig}'s {@code /api/catalog/**} matcher). The
	 * precondition check and the write share one transaction in
	 * {@link com.optiplant.inventory.catalog.application.service.ProductAdminService#changeBaseUnit}
	 * — nothing about that boundary is decided here.
	 */
	@PatchMapping("/{externalId}/base-unit")
	public ProductDetailResponse changeBaseUnit(@PathVariable UUID externalId,
			@Valid @RequestBody ChangeBaseUnitRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetail(manageProductsUseCase.changeBaseUnit(actor, externalId, request.baseUnit()));
	}

	private static boolean parseAscending(String direction) {
		return switch (direction == null ? "asc" : direction) {
			case "asc" -> true;
			case "desc" -> false;
			default -> throw new IllegalArgumentException("direction must be one of: asc, desc");
		};
	}

	private static String blankToNull(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static ProductDetailResponse toDetail(Product product) {
		CategoryRef category = product.category();
		CategoryRefResponse categoryResponse = category == null ? null
				: new CategoryRefResponse(category.externalId(), category.name(), category.active());
		List<ProductUnitResponse> units = product.units().stream()
				.map(ProductController::toUnitResponse).toList();
		return new ProductDetailResponse(product.externalId(), product.sku().value(), product.name(),
				product.description(), product.baseUnit().value(), product.active(), categoryResponse, units,
				product.createdAt(), product.updatedAt());
	}

	private static ProductUnitResponse toUnitResponse(ProductUnit unit) {
		return new ProductUnitResponse(unit.externalId(), unit.unitName().value(), unit.conversionFactor(),
				unit.defaultSaleUnit());
	}

	private static ProductListItemResponse toListItem(ProductSummary summary) {
		CategoryRef category = summary.category();
		CategoryRefResponse categoryResponse = category == null ? null
				: new CategoryRefResponse(category.externalId(), category.name(), category.active());
		return new ProductListItemResponse(summary.externalId(), summary.sku().value(), summary.name(),
				summary.baseUnit().value(), summary.active(), categoryResponse, summary.createdAt(),
				summary.updatedAt());
	}

	public record CreateProductRequest(@NotBlank @Size(max = 50) String sku, @NotBlank @Size(max = 150) String name,
			String description, @NotNull UUID categoryExternalId, @NotBlank @Size(max = 20) String baseUnit,
			List<UnitPayloadRequest> units) {
	}

	public record UnitPayloadRequest(@NotBlank @Size(max = 50) String unitName,
			@NotNull BigDecimal conversionFactor, boolean defaultSaleUnit) {
	}

	/**
	 * {@code baseUnit} is declared only to be rejected — a base-unit change goes
	 * through the dedicated {@code PATCH .../base-unit} endpoint, never through this
	 * one (PA-08, §6.2, DT-07).
	 */
	public record EditProductRequest(@NotBlank @Size(max = 50) String sku, @NotBlank @Size(max = 150) String name,
			String description, @NotNull UUID categoryExternalId, String baseUnit) {
	}

	/** {@code baseUnit} is R-07-normalized inside {@code ProductAdminService}, so no format check here. */
	public record ChangeBaseUnitRequest(@NotBlank @Size(max = 20) String baseUnit) {
	}

	public record CategoryRefResponse(UUID externalId, String name, boolean active) {
	}

	public record ProductUnitResponse(UUID externalId, String unitName, BigDecimal conversionFactor,
			boolean defaultSaleUnit) {
	}

	public record ProductDetailResponse(UUID externalId, String sku, String name, String description, String baseUnit,
			boolean active, CategoryRefResponse category, List<ProductUnitResponse> units, Instant createdAt,
			Instant updatedAt) {
	}

	public record ProductListItemResponse(UUID externalId, String sku, String name, String baseUnit, boolean active,
			CategoryRefResponse category, Instant createdAt, Instant updatedAt) {
	}

	public record ProductPageResponse(List<ProductListItemResponse> content, long totalElements, int page, int size) {
	}
}
