package com.optiplant.inventory.pricing.infrastructure.adapter.in.web;

import com.optiplant.inventory.pricing.application.port.in.ManagePriceListsUseCase;
import com.optiplant.inventory.pricing.application.port.in.ManagePriceListsUseCase.CreatePriceListCommand;
import com.optiplant.inventory.pricing.application.port.in.ManagePriceListsUseCase.PriceListQuery;
import com.optiplant.inventory.pricing.application.port.in.ManagePriceListsUseCase.UpdatePriceListCommand;
import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase;
import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase.PriceQuery;
import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase.SetPriceCommand;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort.PriceListPage;
import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort.PricePage;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * {@code /api/pricing/price-lists/**} — price list CRUD and price collection endpoints (RF-VEN-03, contract §6).
 */
@RestController
@RequestMapping("/api/pricing/price-lists")
public class PriceListController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManagePriceListsUseCase managePriceListsUseCase;
	private final ManagePricesUseCase managePricesUseCase;
	private final PrincipalAccessor principalAccessor;

	public PriceListController(ManagePriceListsUseCase managePriceListsUseCase,
			ManagePricesUseCase managePricesUseCase, PrincipalAccessor principalAccessor) {
		this.managePriceListsUseCase = managePriceListsUseCase;
		this.managePricesUseCase = managePricesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public ResponseEntity<PriceListResponse> create(@Valid @RequestBody CreatePriceListRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		CreatePriceListCommand command = new CreatePriceListCommand(
				request.code(),
				request.name(),
				request.description(),
				request.maxDiscountPercent()
		);
		PriceList created = managePriceListsUseCase.create(actor, command);
		return ResponseEntity.status(HttpStatus.CREATED).body(toPriceListResponse(created));
	}

	@GetMapping
	public PriceListPageResponse list(
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		PriceListPage result = managePriceListsUseCase.list(actor,
				new PriceListQuery(active, Math.max(page, 0), resolveSize(size)));
		List<PriceListResponse> content = result.content().stream()
				.map(PriceListController::toPriceListResponse)
				.toList();
		return new PriceListPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/{externalId}")
	public PriceListResponse get(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toPriceListResponse(managePriceListsUseCase.get(actor, externalId));
	}

	@PutMapping("/{externalId}")
	public PriceListResponse update(@PathVariable UUID externalId, @Valid @RequestBody UpdatePriceListRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		UpdatePriceListCommand command = new UpdatePriceListCommand(
				request.name(),
				request.description(),
				request.maxDiscountPercent()
		);
		return toPriceListResponse(managePriceListsUseCase.update(actor, externalId, command));
	}

	@PatchMapping("/{externalId}/deactivation")
	public PriceListResponse deactivate(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toPriceListResponse(managePriceListsUseCase.deactivate(actor, externalId));
	}

	@PostMapping("/{externalId}/prices")
	public ResponseEntity<PriceResponse> setPrice(
			@PathVariable UUID externalId,
			@Valid @RequestBody SetPriceRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		SetPriceCommand command = new SetPriceCommand(
				request.productExternalId(),
				request.branchExternalId(),
				request.unitPrice(),
				request.validFrom()
		);
		Price price = managePricesUseCase.setPrice(actor, externalId, command);
		return ResponseEntity.status(HttpStatus.CREATED).body(toPriceResponse(price));
	}

	@GetMapping("/{externalId}/prices")
	public PricePageResponse listPrices(
			@PathVariable UUID externalId,
			@RequestParam(required = false) UUID productExternalId,
			@RequestParam(required = false) UUID branchExternalId,
			@RequestParam(required = false) Boolean currentOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		PricePage result = managePricesUseCase.listPrices(actor, externalId,
				new PriceQuery(productExternalId, branchExternalId, currentOnly, Math.max(page, 0), resolveSize(size)));
		List<PriceResponse> content = result.content().stream()
				.map(PriceListController::toPriceResponse)
				.toList();
		return new PricePageResponse(content, result.totalElements(), result.page(), result.size());
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

	static PriceListResponse toPriceListResponse(PriceList priceList) {
		return new PriceListResponse(
				priceList.externalId(),
				priceList.code().value(),
				priceList.name().value(),
				priceList.description(),
				priceList.maxDiscountPercent().value(),
				priceList.isDefault(),
				priceList.active(),
				priceList.createdAt(),
				priceList.updatedAt()
		);
	}

	static PriceResponse toPriceResponse(Price price) {
		return new PriceResponse(
				price.externalId(),
				price.priceListExternalId(),
				price.productExternalId(),
				price.branchExternalId(),
				price.unitPrice().value(),
				price.validity().from(),
				price.validity().to(),
				price.createdAt()
		);
	}

	public record CreatePriceListRequest(
			@NotBlank String code,
			@NotBlank String name,
			String description,
			@NotNull BigDecimal maxDiscountPercent
	) {
	}

	public record UpdatePriceListRequest(
			@NotBlank String name,
			String description,
			@NotNull BigDecimal maxDiscountPercent
	) {
	}

	public record SetPriceRequest(
			@NotNull UUID productExternalId,
			UUID branchExternalId,
			@NotNull BigDecimal unitPrice,
			LocalDate validFrom
	) {
	}

	public record PriceListResponse(
			UUID externalId,
			String code,
			String name,
			String description,
			BigDecimal maxDiscountPercent,
			boolean isDefault,
			boolean active,
			Instant createdAt,
			Instant updatedAt
	) {
	}

	public record PriceResponse(
			UUID externalId,
			UUID priceListExternalId,
			UUID productExternalId,
			UUID branchExternalId,
			BigDecimal unitPrice,
			LocalDate validFrom,
			LocalDate validTo,
			Instant createdAt
	) {
	}

	public record PriceListPageResponse(
			List<PriceListResponse> content,
			long totalElements,
			int page,
			int size
	) {
	}

	public record PricePageResponse(
			List<PriceResponse> content,
			long totalElements,
			int page,
			int size
	) {
	}
}
