package com.optiplant.inventory.catalog.infrastructure.adapter.in.web;

import com.optiplant.inventory.catalog.application.port.in.ManageProductUnitsUseCase;
import com.optiplant.inventory.catalog.application.port.in.ManageProductUnitsUseCase.UnitCommand;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/catalog/products/{productExternalId}/units/**} — the four unit
 * endpoints of contract §6.3. Reads are open to every authenticated role and
 * mutations are {@code ADMIN}-only; both are enforced by {@code SecurityConfig}'s
 * method-scoped {@code /api/catalog/**} matchers, not here (design §7, D-1),
 * mirroring {@code ProductController}.
 *
 * <p>The collection is <strong>not paginated</strong>: it is bounded by the
 * product and by {@code uq_product_unit} — the justified exception to RNF-PER-04
 * (contract §6.3). A unit that belongs to another product resolves to {@code 404},
 * never {@code 200}. {@code DELETE} returns {@code 204}. {@code POST} returns
 * {@code 201 Created} with a {@code Location} header carrying {@code external_id}
 * values only (§7.1 point 1).
 */
@RestController
@RequestMapping("/api/catalog/products/{productExternalId}/units")
public class ProductUnitController {

	private final ManageProductUnitsUseCase manageProductUnitsUseCase;
	private final PrincipalAccessor principalAccessor;

	public ProductUnitController(ManageProductUnitsUseCase manageProductUnitsUseCase,
			PrincipalAccessor principalAccessor) {
		this.manageProductUnitsUseCase = manageProductUnitsUseCase;
		this.principalAccessor = principalAccessor;
	}

	@GetMapping
	public List<ProductUnitResponse> list(@PathVariable UUID productExternalId) {
		return manageProductUnitsUseCase.list(productExternalId).stream()
				.map(ProductUnitController::toResponse)
				.toList();
	}

	@PostMapping
	public ResponseEntity<ProductUnitResponse> add(@PathVariable UUID productExternalId,
			@Valid @RequestBody UnitRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		ProductUnit created = manageProductUnitsUseCase.add(actor, productExternalId,
				new UnitCommand(request.unitName(), request.conversionFactor(), request.defaultSaleUnit()));
		ProductUnitResponse body = toResponse(created);
		return ResponseEntity
				.created(URI.create("/api/catalog/products/" + productExternalId + "/units/" + body.externalId()))
				.body(body);
	}

	@PutMapping("/{unitExternalId}")
	public ProductUnitResponse replace(@PathVariable UUID productExternalId, @PathVariable UUID unitExternalId,
			@Valid @RequestBody UnitRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toResponse(manageProductUnitsUseCase.replace(actor, productExternalId, unitExternalId,
				new UnitCommand(request.unitName(), request.conversionFactor(), request.defaultSaleUnit())));
	}

	@DeleteMapping("/{unitExternalId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID productExternalId, @PathVariable UUID unitExternalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		manageProductUnitsUseCase.delete(actor, productExternalId, unitExternalId);
	}

	private static ProductUnitResponse toResponse(ProductUnit unit) {
		return new ProductUnitResponse(unit.externalId(), unit.unitName().value(), unit.conversionFactor(),
				unit.defaultSaleUnit(), unit.createdAt());
	}

	public record UnitRequest(@NotBlank @Size(max = 50) String unitName, @NotNull BigDecimal conversionFactor,
			boolean defaultSaleUnit) {
	}

	public record ProductUnitResponse(UUID externalId, String unitName, BigDecimal conversionFactor,
			boolean defaultSaleUnit, Instant createdAt) {
	}
}
