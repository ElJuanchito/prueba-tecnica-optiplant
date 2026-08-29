package com.optiplant.inventory.catalog.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optiplant.inventory.catalog.application.port.in.ManageProductsUseCase;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort.NewProduct;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort.NewUnitRow;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort.ProductFilter;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort.ProductPage;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort.ProductUpdate;
import com.optiplant.inventory.catalog.domain.exception.CategoryInactiveException;
import com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException;
import com.optiplant.inventory.catalog.domain.exception.DuplicateSkuException;
import com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.catalog.domain.model.Sku;
import com.optiplant.inventory.catalog.domain.model.StockPresence;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import com.optiplant.inventory.catalog.domain.service.BaseUnitChangePolicy;
import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.ProductStockPresencePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates product administration (CU-INV-01): create, edit, disable, enable,
 * query. Every mutation resolves the referenced category first, applies the domain
 * rules, persists, and writes its audit entry through {@link AuditWritePort} in
 * the same transaction ({@code entityName = "products"}, {@code branchId = null} —
 * the catalog is corporate, R-15, R-16). Reads take no actor and are
 * {@code readOnly}.
 *
 * <p>{@code changeBaseUnit} (R-08) depends on an {@code Optional<ProductStockPresencePort>}
 * so the {@code Optional} stays empty — and the rule fails closed, never open — in
 * any deployment where no adapter implements the port. {@code inventory}'s
 * {@code InventoryStockPresenceAdapter} is now that implementation (DT-07 paid), and
 * {@code ProductController} exposes the rule at
 * {@code PATCH /products/{externalId}/base-unit}. With no bean present the
 * {@code Optional} is still empty, {@link #presenceOf} yields {@link StockPresence#UNKNOWN}
 * and {@link BaseUnitChangePolicy} refuses — fail closed (contract §2.2).
 */
@Service
public class ProductAdminService implements ManageProductsUseCase {

	private final ProductRepositoryPort productRepository;
	private final CategoryRepositoryPort categoryRepository;
	private final AuditWritePort auditWritePort;

	/**
	 * The R-08 precondition source. Spring supplies {@code Optional.empty()} when no
	 * bean implements the interface — the state of this change, since {@code inventory}
	 * is not built. {@link #presenceOf} maps the empty case to
	 * {@link StockPresence#UNKNOWN}, which {@link BaseUnitChangePolicy} refuses.
	 */
	private final Optional<ProductStockPresencePort> stockPresencePort;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** Transient id for the validation-only aggregate built in {@link #create}; persistence assigns the real one. */
	private static final UUID PLACEHOLDER_ID = new UUID(0L, 0L);

	public ProductAdminService(ProductRepositoryPort productRepository, CategoryRepositoryPort categoryRepository,
			AuditWritePort auditWritePort, Optional<ProductStockPresencePort> stockPresencePort) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.auditWritePort = auditWritePort;
		this.stockPresencePort = stockPresencePort;
	}

	@Override
	@Transactional(readOnly = true)
	public ProductPage list(ProductQuery query) {
		return productRepository.list(new ProductFilter(query.q(), query.categoryExternalId(), query.active(),
				query.sort(), query.ascending(), query.page(), query.size()));
	}

	@Override
	@Transactional(readOnly = true)
	public Product get(UUID externalId) {
		return productRepository.findByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));
	}

	@Override
	@Transactional
	public Product create(AuthenticatedPrincipal actor, CreateProductCommand command) {
		Sku sku = new Sku(command.sku());
		UnitCode baseUnit = UnitCode.baseUnit(command.baseUnit());
		CategoryRef category = resolveActiveCategory(command.categoryExternalId());
		requireUniqueSku(sku, null);

		List<ProductUnit> units = toDomainUnits(command.units());
		// Constructing the aggregate is the single place R-13 (no duplicate unit name,
		// no base-unit homonym with factor != 1) and R-14 (at most one default sale
		// unit) are asserted — done here so a bad inline-units payload fails before any
		// SQL is issued (design §8.2). The id/timestamps are placeholders; persistence
		// assigns the real ones.
		new Product(PLACEHOLDER_ID, sku, command.name(), command.description(), baseUnit, true, category, units,
				Instant.EPOCH, Instant.EPOCH);

		List<NewUnitRow> rows = units.stream()
				.map(u -> new NewUnitRow(u.unitName().value(), u.conversionFactor(), u.defaultSaleUnit()))
				.toList();
		Product created = productRepository.create(new NewProduct(sku.value(), command.name(), command.description(),
				category.externalId(), baseUnit.value(), rows));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.CREATE.name(), "products",
				created.externalId().toString(), null, serializePayload(created), null));
		return created;
	}

	@Override
	@Transactional
	public Product edit(AuthenticatedPrincipal actor, UUID externalId, EditProductCommand command) {
		Product existing = productRepository.findByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));

		Sku sku = new Sku(command.sku());
		CategoryRef category = resolveActiveCategory(command.categoryExternalId());
		requireUniqueSku(sku, externalId);

		Product updated = productRepository.update(externalId, new ProductUpdate(sku.value(), command.name(),
				command.description(), category.externalId(), Instant.now()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.UPDATE.name(), "products",
				externalId.toString(), serializePayload(existing), serializePayload(updated), null));
		return updated;
	}

	@Override
	@Transactional
	public Product disable(AuthenticatedPrincipal actor, UUID externalId) {
		Product existing = productRepository.findByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));

		if (!existing.active()) {
			return existing; // idempotent — already disabled, nothing to mutate or audit (R-10)
		}

		Product updated = productRepository.setActive(externalId, false, Instant.now());

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.DISABLE.name(), "products",
				externalId.toString(), serializePayload(existing), serializePayload(updated), null));
		return updated;
	}

	@Override
	@Transactional
	public Product enable(AuthenticatedPrincipal actor, UUID externalId) {
		Product existing = productRepository.findByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));

		if (existing.active()) {
			return existing; // idempotent — already active, nothing to mutate or audit (R-11)
		}

		// R-11: re-enabling must not recreate the inconsistency R-04/R-05 prevent — the
		// product's category must be active. The embedded ref was loaded in this
		// transaction, so it reflects the current state.
		if (existing.category() != null && !existing.category().active()) {
			throw new CategoryInactiveException("category " + existing.category().externalId()
					+ " is inactive; re-enable it before the product");
		}

		Product updated = productRepository.setActive(externalId, true, Instant.now());

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.ENABLE.name(), "products",
				externalId.toString(), serializePayload(existing), serializePayload(updated), null));
		return updated;
	}

	@Override
	@Transactional
	public Product changeBaseUnit(AuthenticatedPrincipal actor, UUID externalId, String newBaseUnit) {
		Product existing = productRepository.findByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));

		// R-07 normalization applies identically to the controller and to a test.
		UnitCode baseUnit = UnitCode.baseUnit(newBaseUnit);
		Instant now = Instant.now();

		// The port call, the policy and the setBaseUnit write share this one
		// transaction (contract §8): a concurrent goods receipt cannot create the
		// first movement between the check and the commit. BaseUnitChangePolicy
		// throws BaseUnitChangeRejectedException on HAS_HISTORY / UNKNOWN, so a
		// refusal leaves nothing written and no audit entry (R-08).
		BaseUnitChangePolicy.apply(existing, baseUnit, presenceOf(externalId), now);

		Product updated = productRepository.setBaseUnit(externalId, baseUnit.value(), now);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.UPDATE.name(), "products",
				externalId.toString(), serializePayload(existing), serializePayload(updated), null));
		return updated;
	}

	/**
	 * Maps the {@code Optional<ProductStockPresencePort>} bean to a
	 * {@link StockPresence} (design §5.2). {@code orElse(StockPresence.UNKNOWN)} is
	 * the fail-closed default and {@link BaseUnitChangePolicy} refuses on
	 * {@code UNKNOWN}, so there is no arrangement of these lines that lets the
	 * change through unproven (contract §2.2).
	 */
	private StockPresence presenceOf(UUID productExternalId) {
		return stockPresencePort
				.map(port -> port.isProductUntouched(productExternalId)
						? StockPresence.UNTOUCHED : StockPresence.HAS_HISTORY)
				.orElse(StockPresence.UNKNOWN);
	}

	private CategoryRef resolveActiveCategory(UUID categoryExternalId) {
		CategoryRef category = categoryRepository.findRefByExternalId(categoryExternalId)
				.orElseThrow(() -> new CategoryNotFoundException(categoryExternalId));
		if (!category.active()) {
			throw new CategoryInactiveException("category " + categoryExternalId + " is inactive");
		}
		return category;
	}

	private void requireUniqueSku(Sku sku, UUID excludingExternalId) {
		if (productRepository.existsBySku(sku.value(), excludingExternalId)) {
			throw new DuplicateSkuException("SKU '" + sku.value() + "' is already in use");
		}
	}

	private static List<ProductUnit> toDomainUnits(List<NewUnit> units) {
		if (units == null) {
			return List.of();
		}
		return units.stream()
				.map(u -> new ProductUnit(PLACEHOLDER_ID, new UnitCode(u.unitName()), u.conversionFactor(),
						u.defaultSaleUnit(), Instant.EPOCH))
				.toList();
	}

	private String serializePayload(Product product) {
		if (product == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(ProductAuditPayload.from(product));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record ProductAuditPayload(String sku, String name, String description, String baseUnit, boolean active,
			UUID categoryExternalId, List<UnitPayload> units) {

		static ProductAuditPayload from(Product product) {
			return new ProductAuditPayload(product.sku().value(), product.name(), product.description(),
					product.baseUnit().value(), product.active(),
					product.category() == null ? null : product.category().externalId(),
					product.units().stream().map(UnitPayload::from).toList());
		}

		record UnitPayload(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {
			static UnitPayload from(ProductUnit unit) {
				return new UnitPayload(unit.unitName().value(), unit.conversionFactor(), unit.defaultSaleUnit());
			}
		}
	}
}
