package com.optiplant.inventory.sales.infrastructure.adapter.in.web;

import com.optiplant.inventory.sales.application.port.in.QuerySalesUseCase;
import com.optiplant.inventory.sales.application.port.in.QuerySalesUseCase.SaleListQuery;
import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase;
import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleCommand;
import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase.RegisterSaleItemCommand;
import com.optiplant.inventory.sales.application.port.in.VoidSaleUseCase;
import com.optiplant.inventory.sales.application.port.in.VoidSaleUseCase.VoidSaleCommand;
import com.optiplant.inventory.sales.domain.model.BranchRef;
import com.optiplant.inventory.sales.domain.model.PriceListRef;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SaleItemView;
import com.optiplant.inventory.sales.domain.model.SalePage;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.sales.domain.model.SaleSummary;
import com.optiplant.inventory.sales.domain.model.UserRef;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/sales/**} — the five internal endpoints of contract §6.
 *
 * <p>No endpoint accepts a branch parameter (RN-14): the acting branch is always derived
 * from the session principal. Page sizes above {@value #MAX_PAGE_SIZE} are rejected (R-00).
 * No numeric id and no raw F-3 token ever appear in responses.
 */
@RestController
@RequestMapping("/api/sales")
public class SaleController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final RegisterSaleUseCase registerSaleUseCase;
	private final VoidSaleUseCase voidSaleUseCase;
	private final QuerySalesUseCase querySalesUseCase;
	private final PrincipalAccessor principalAccessor;

	public SaleController(RegisterSaleUseCase registerSaleUseCase, VoidSaleUseCase voidSaleUseCase,
			QuerySalesUseCase querySalesUseCase, PrincipalAccessor principalAccessor) {
		this.registerSaleUseCase = registerSaleUseCase;
		this.voidSaleUseCase = voidSaleUseCase;
		this.querySalesUseCase = querySalesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public ResponseEntity<SaleDetailResponse> register(@Valid @RequestBody RegisterSaleRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<RegisterSaleItemCommand> items = request.items() == null ? List.of() : request.items().stream()
				.map(item -> new RegisterSaleItemCommand(
						item.productExternalId(),
						item.quantity(),
						item.unitOfMeasureExternalId(),
						item.discountPercent()
				))
				.toList();

		RegisterSaleCommand command = new RegisterSaleCommand(
				request.priceListExternalId(),
				request.customerName(),
				request.customerTaxId(),
				request.taxPercent(),
				request.notes(),
				items,
				null
		);

		SaleDetail detail = registerSaleUseCase.register(actor, command);
		return ResponseEntity.status(HttpStatus.CREATED).body(toDetailResponse(detail));
	}

	@GetMapping
	public SalePageResponse list(
			@RequestParam(required = false) SaleStatus status,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		SalePage result = querySalesUseCase.list(actor,
				new SaleListQuery(status, from, to, Math.max(page, 0), resolveSize(size), sort));

		List<SaleSummaryResponse> content = result.content().stream()
				.map(SaleController::toSummaryResponse)
				.toList();
		SaleAggregatesResponse agg = new SaleAggregatesResponse(
				result.aggregates().salesCount(),
				result.aggregates().totalAmount()
		);
		return new SalePageResponse(content, result.totalElements(), result.page(), result.size(), agg);
	}

	@GetMapping("/{externalId}")
	public SaleDetailResponse detail(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(querySalesUseCase.detail(actor, externalId));
	}

	@GetMapping("/by-invoice/{invoiceNumber}")
	public SaleDetailResponse byInvoiceNumber(@PathVariable String invoiceNumber) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(querySalesUseCase.byInvoiceNumber(actor, invoiceNumber));
	}

	@PostMapping("/{externalId}/cancellation")
	public SaleDetailResponse cancel(@PathVariable UUID externalId, @Valid @RequestBody CancellationRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(voidSaleUseCase.voidSale(actor, externalId, new VoidSaleCommand(request.reason())));
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

	static SaleDetailResponse toDetailResponse(SaleDetail detail) {
		List<SaleItemResponse> items = detail.items().stream()
				.map(SaleController::toItemResponse)
				.toList();
		return new SaleDetailResponse(
				detail.externalId(),
				detail.invoiceNumber(),
				detail.status(),
				toBranchRefResponse(detail.branch()),
				toUserRefResponse(detail.soldBy()),
				toPriceListRefResponse(detail.priceList()),
				detail.customerName(),
				detail.customerTaxId(),
				detail.subtotal(),
				detail.discountAmount(),
				detail.taxAmount(),
				detail.totalAmount(),
				detail.notes(),
				detail.cancellationReason(),
				detail.createdAt(),
				items
		);
	}

	private static SaleSummaryResponse toSummaryResponse(SaleSummary summary) {
		return new SaleSummaryResponse(
				summary.externalId(),
				summary.invoiceNumber(),
				summary.status(),
				toBranchRefResponse(summary.branch()),
				toUserRefResponse(summary.soldBy()),
				toPriceListRefResponse(summary.priceList()),
				summary.customerName(),
				summary.totalAmount(),
				summary.createdAt()
		);
	}

	private static BranchRefResponse toBranchRefResponse(BranchRef ref) {
		return ref == null ? null : new BranchRefResponse(ref.externalId(), ref.name());
	}

	private static UserRefResponse toUserRefResponse(UserRef ref) {
		return ref == null ? null : new UserRefResponse(ref.externalId(), ref.username());
	}

	private static PriceListRefResponse toPriceListRefResponse(PriceListRef ref) {
		return ref == null ? null : new PriceListRefResponse(ref.externalId(), ref.code(), ref.maxDiscountPercent());
	}

	private static SaleItemResponse toItemResponse(SaleItemView item) {
		return new SaleItemResponse(
				item.externalId(),
				item.productExternalId(),
				item.sku(),
				item.name(),
				item.quantity(),
				item.listUnitPrice(),
				item.unitPrice(),
				item.discountPercent(),
				item.subtotal()
		);
	}

	public record RegisterSaleRequest(
			UUID priceListExternalId,
			@NotBlank String customerName,
			String customerTaxId,
			BigDecimal taxPercent,
			String notes,
			@NotEmpty List<@Valid RegisterSaleItemRequest> items
	) {
	}

	public record RegisterSaleItemRequest(
			@NotNull UUID productExternalId,
			@NotNull BigDecimal quantity,
			UUID unitOfMeasureExternalId,
			BigDecimal discountPercent
	) {
	}

	public record CancellationRequest(String reason) {
	}

	public record BranchRefResponse(UUID externalId, String name) {
	}

	public record UserRefResponse(UUID externalId, String username) {
	}

	public record PriceListRefResponse(UUID externalId, String code, BigDecimal maxDiscountPercent) {
	}

	public record SaleItemResponse(
			UUID externalId,
			UUID productExternalId,
			String sku,
			String name,
			BigDecimal quantity,
			BigDecimal listUnitPrice,
			BigDecimal unitPrice,
			BigDecimal discountPercent,
			BigDecimal subtotal
	) {
	}

	public record SaleDetailResponse(
			UUID externalId,
			String invoiceNumber,
			SaleStatus status,
			BranchRefResponse branch,
			UserRefResponse soldBy,
			PriceListRefResponse priceList,
			String customerName,
			String customerTaxId,
			BigDecimal subtotal,
			BigDecimal discountAmount,
			BigDecimal taxAmount,
			BigDecimal totalAmount,
			String notes,
			String cancellationReason,
			Instant createdAt,
			List<SaleItemResponse> items
	) {
	}

	public record SaleSummaryResponse(
			UUID externalId,
			String invoiceNumber,
			SaleStatus status,
			BranchRefResponse branch,
			UserRefResponse soldBy,
			PriceListRefResponse priceList,
			String customerName,
			BigDecimal totalAmount,
			Instant createdAt
	) {
	}

	public record SaleAggregatesResponse(long salesCount, BigDecimal totalAmount) {
	}

	public record SalePageResponse(
			List<SaleSummaryResponse> content,
			long totalElements,
			int page,
			int size,
			SaleAggregatesResponse aggregates
	) {
	}
}
