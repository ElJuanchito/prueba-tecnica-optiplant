package com.optiplant.inventory.purchases.infrastructure.adapter.in.web;

import com.optiplant.inventory.purchases.application.port.in.ManageSuppliersUseCase;
import com.optiplant.inventory.purchases.application.port.in.ManageSuppliersUseCase.CreateSupplierCommand;
import com.optiplant.inventory.purchases.application.port.in.ManageSuppliersUseCase.EditSupplierCommand;
import com.optiplant.inventory.purchases.application.port.in.ManageSuppliersUseCase.SupplierQuery;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.Supplier;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
 * REST controller for supplier management (CU-COM-01, contract §6).
 * Corporate data — no branch scoping (R-02).
 */
@RestController
@RequestMapping("/api/purchases/suppliers")
public class SupplierController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageSuppliersUseCase manageSuppliersUseCase;
	private final PrincipalAccessor principalAccessor;

	public SupplierController(ManageSuppliersUseCase manageSuppliersUseCase, PrincipalAccessor principalAccessor) {
		this.manageSuppliersUseCase = manageSuppliersUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public ResponseEntity<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		Supplier created = manageSuppliersUseCase.create(actor, request.toCommand());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
	}

	@GetMapping
	public SupplierPageResponse list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort) {
		PurchasePage<Supplier> result = manageSuppliersUseCase.list(
				new SupplierQuery(search, active, Math.max(page, 0), resolveSize(size), sort));
		List<SupplierResponse> content = result.content().stream()
				.map(SupplierController::toResponse)
				.toList();
		return new SupplierPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/{externalId}")
	public SupplierResponse get(@PathVariable UUID externalId) {
		return toResponse(manageSuppliersUseCase.get(externalId));
	}

	@PutMapping("/{externalId}")
	public SupplierResponse edit(@PathVariable UUID externalId, @Valid @RequestBody EditSupplierRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		Supplier updated = manageSuppliersUseCase.edit(actor, externalId, request.toCommand());
		return toResponse(updated);
	}

	@PatchMapping("/{externalId}/disable")
	public SupplierResponse disable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toResponse(manageSuppliersUseCase.disable(actor, externalId));
	}

	@PatchMapping("/{externalId}/enable")
	public SupplierResponse enable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toResponse(manageSuppliersUseCase.enable(actor, externalId));
	}

	private static int resolveSize(Integer size) {
		if (size == null) {
			return DEFAULT_PAGE_SIZE;
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		return size;
	}

	private static SupplierResponse toResponse(Supplier supplier) {
		return new SupplierResponse(
				supplier.externalId(),
				supplier.taxId().value(),
				supplier.name().value(),
				supplier.contact() != null ? supplier.contact().contactName() : null,
				supplier.contact() != null ? supplier.contact().email() : null,
				supplier.contact() != null ? supplier.contact().phone() : null,
				supplier.contact() != null ? supplier.contact().address() : null,
				supplier.active(),
				supplier.createdAt(),
				supplier.updatedAt()
		);
	}

	public record CreateSupplierRequest(
			@NotBlank String taxId,
			@NotBlank String name,
			String contactName,
			String email,
			String phone,
			String address
	) {
		public CreateSupplierCommand toCommand() {
			return new CreateSupplierCommand(taxId, name, contactName, email, phone, address);
		}
	}

	public record EditSupplierRequest(
			@NotBlank String name,
			String contactName,
			String email,
			String phone,
			String address
	) {
		public EditSupplierCommand toCommand() {
			return new EditSupplierCommand(name, contactName, email, phone, address);
		}
	}

	public record SupplierResponse(
			UUID externalId,
			String taxId,
			String name,
			String contactName,
			String email,
			String phone,
			String address,
			boolean active,
			Instant createdAt,
			Instant updatedAt
	) {
	}

	public record SupplierPageResponse(
			List<SupplierResponse> content,
			long totalElements,
			int page,
			int size
	) {
	}
}
