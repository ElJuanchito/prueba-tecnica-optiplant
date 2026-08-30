package com.optiplant.inventory.sales.infrastructure.adapter.in.web.customer;

import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase;
import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase.CreateCustomerCommand;
import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase.CustomerQuery;
import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase.EditCustomerCommand;
import com.optiplant.inventory.sales.application.port.in.QuerySalesUseCase;
import com.optiplant.inventory.sales.application.port.in.QuerySalesUseCase.SaleListQuery;
import com.optiplant.inventory.sales.domain.model.BranchRef;
import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerPage;
import com.optiplant.inventory.sales.domain.model.CustomerRef;
import com.optiplant.inventory.sales.domain.model.PriceListRef;
import com.optiplant.inventory.sales.domain.model.SalePage;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.sales.domain.model.SaleSummary;
import com.optiplant.inventory.sales.domain.model.UserRef;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
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
 * REST controller for customer management and customer sales history (CU-VEN-05, CU-VEN-06, design §7).
 */
@RestController
@RequestMapping("/api/sales/customers")
public class CustomerController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageCustomersUseCase manageCustomersUseCase;
	private final QuerySalesUseCase querySalesUseCase;
	private final PrincipalAccessor principalAccessor;

	public CustomerController(
			ManageCustomersUseCase manageCustomersUseCase,
			QuerySalesUseCase querySalesUseCase,
			PrincipalAccessor principalAccessor
	) {
		this.manageCustomersUseCase = manageCustomersUseCase;
		this.querySalesUseCase = querySalesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		Customer created = manageCustomersUseCase.create(actor, request.toCommand());
		return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(created));
	}

	@GetMapping
	public CustomerPageResponse list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort
	) {
		CustomerPage customerPage = manageCustomersUseCase.list(
				new CustomerQuery(search, active, Math.max(page, 0), resolveSize(size), sort)
		);
		return CustomerPageResponse.from(customerPage);
	}

	@GetMapping("/{externalId}")
	public CustomerResponse get(@PathVariable UUID externalId) {
		return CustomerResponse.from(manageCustomersUseCase.get(externalId));
	}

	@PutMapping("/{externalId}")
	public CustomerResponse edit(
			@PathVariable UUID externalId,
			@Valid @RequestBody EditCustomerRequest request
	) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		Customer updated = manageCustomersUseCase.edit(actor, externalId, request.toCommand());
		return CustomerResponse.from(updated);
	}

	@PatchMapping("/{externalId}/disable")
	public CustomerResponse disable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return CustomerResponse.from(manageCustomersUseCase.disable(actor, externalId));
	}

	@PatchMapping("/{externalId}/enable")
	public CustomerResponse enable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return CustomerResponse.from(manageCustomersUseCase.enable(actor, externalId));
	}

	@GetMapping("/{externalId}/sales")
	public SalePageResponse history(
			@PathVariable UUID externalId,
			@RequestParam(required = false) SaleStatus status,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort
	) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		manageCustomersUseCase.get(externalId);

		SalePage result = querySalesUseCase.list(actor,
				new SaleListQuery(status, from, to, Math.max(page, 0), resolveSize(size), sort, externalId));

		List<SaleSummaryResponse> content = result.content().stream()
				.map(CustomerController::toSummaryResponse)
				.toList();
		SaleAggregatesResponse agg = new SaleAggregatesResponse(
				result.aggregates().salesCount(),
				result.aggregates().totalAmount()
		);
		return new SalePageResponse(content, result.totalElements(), result.page(), result.size(), agg);
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

	private static SaleSummaryResponse toSummaryResponse(SaleSummary summary) {
		return new SaleSummaryResponse(
				summary.externalId(),
				summary.invoiceNumber(),
				summary.status(),
				toBranchRefResponse(summary.branch()),
				toUserRefResponse(summary.soldBy()),
				toPriceListRefResponse(summary.priceList()),
				toCustomerRefResponse(summary.customer()),
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

	private static CustomerRefResponse toCustomerRefResponse(CustomerRef ref) {
		return ref == null ? null : new CustomerRefResponse(ref.externalId(), ref.name(), ref.taxId());
	}

	public record CreateCustomerRequest(
			@NotBlank String name,
			String taxId,
			String email,
			String phone,
			String address
	) {
		public CreateCustomerCommand toCommand() {
			return new CreateCustomerCommand(name, taxId, email, phone, address);
		}
	}

	public record EditCustomerRequest(
			@NotBlank String name,
			String taxId,
			String email,
			String phone,
			String address
	) {
		public EditCustomerCommand toCommand() {
			return new EditCustomerCommand(name, taxId, email, phone, address);
		}
	}

	public record CustomerResponse(
			UUID externalId,
			String name,
			String taxId,
			String email,
			String phone,
			String address,
			boolean active,
			Instant createdAt,
			Instant updatedAt
	) {
		public static CustomerResponse from(Customer customer) {
			return new CustomerResponse(
					customer.externalId(),
					customer.name().value(),
					customer.taxId() != null ? customer.taxId().value() : null,
					customer.contact() != null ? customer.contact().email() : null,
					customer.contact() != null ? customer.contact().phone() : null,
					customer.contact() != null ? customer.contact().address() : null,
					customer.active(),
					customer.createdAt(),
					customer.updatedAt()
			);
		}
	}

	public record CustomerPageResponse(
			List<CustomerResponse> content,
			long totalElements,
			int page,
			int size
	) {
		public static CustomerPageResponse from(CustomerPage page) {
			List<CustomerResponse> content = page.content().stream()
					.map(CustomerResponse::from)
					.toList();
			return new CustomerPageResponse(content, page.totalElements(), page.page(), page.size());
		}
	}

	public record BranchRefResponse(UUID externalId, String name) {
	}

	public record UserRefResponse(UUID externalId, String username) {
	}

	public record PriceListRefResponse(UUID externalId, String code, BigDecimal maxDiscountPercent) {
	}

	public record CustomerRefResponse(UUID externalId, String name, String taxId) {
	}

	public record SaleSummaryResponse(
			UUID externalId,
			String invoiceNumber,
			SaleStatus status,
			BranchRefResponse branch,
			UserRefResponse soldBy,
			PriceListRefResponse priceList,
			CustomerRefResponse customer,
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
