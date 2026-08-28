package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductSummary;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * The single {@link ProductRepositoryPort} implementation and the only class in
 * {@code catalog} that touches a product's internal numeric {@code id} — every
 * returned value is an {@code external_id} UUID or a domain record (design §6.2).
 *
 * <p>{@link #create} persists the product and its inline units in one save via the
 * {@code @OneToMany(cascade = ALL)} association (R-06). {@link #list} builds the
 * {@link Sort} from the closed {@code ProductSort} allow-list, so the sort column
 * is chosen in Java and never interpolated (R-12, D-10).
 */
@Component
public class ProductPersistenceAdapter implements ProductRepositoryPort {

	private final ProductSpringDataRepository productRepository;
	private final CategorySpringDataRepository categoryRepository;
	private final ProductMapper productMapper;

	public ProductPersistenceAdapter(ProductSpringDataRepository productRepository,
			CategorySpringDataRepository categoryRepository, ProductMapper productMapper) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.productMapper = productMapper;
	}

	@Override
	public Optional<Product> findByExternalId(UUID externalId) {
		return productRepository.findByExternalIdWithUnits(externalId).map(productMapper::toDomain);
	}

	@Override
	public boolean existsBySku(String normalizedSku, UUID excludingExternalId) {
		return productRepository.existsBySku(normalizedSku, excludingExternalId);
	}

	/**
	 * Persists the product and its inline units in one save via the
	 * {@code @OneToMany(cascade = ALL)} association (R-06).
	 *
	 * <p><strong>No default-sale-unit clearing step is needed here</strong>,
	 * unlike {@code ProductUnitPersistenceAdapter#add} / {@code replace} (design
	 * §8.2): the product is brand new, so no sibling row can already hold
	 * {@code is_default_sale_unit = TRUE}, and {@code Product}'s compact
	 * constructor has already rejected a payload carrying two defaults inside
	 * {@code ProductAdminService.create} before any SQL is issued. The
	 * {@code uq_product_units_single_default} partial index therefore never sees an
	 * intermediate two-{@code TRUE} state on this path.
	 */
	@Override
	public Product create(NewProduct newProduct) {
		ProductJpaEntity entity = new ProductJpaEntity();
		entity.setCategory(requireCategory(newProduct.categoryExternalId()));
		entity.setSku(newProduct.sku());
		entity.setName(newProduct.name());
		entity.setDescription(newProduct.description());
		entity.setBaseUnit(newProduct.baseUnit());
		entity.setActive(true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		for (NewUnitRow row : newProduct.units()) {
			ProductUnitJpaEntity unit = new ProductUnitJpaEntity();
			unit.setUnitName(row.unitName());
			unit.setConversionFactor(row.conversionFactor());
			unit.setDefaultSaleUnit(row.defaultSaleUnit());
			unit.setCreatedAt(now);
			entity.addUnit(unit);
		}
		return productMapper.toDomain(productRepository.save(entity));
	}

	@Override
	public Product update(UUID externalId, ProductUpdate update) {
		ProductJpaEntity entity = requireProduct(externalId);
		entity.setSku(update.sku());
		entity.setName(update.name());
		entity.setDescription(update.description());
		entity.setCategory(requireCategory(update.categoryExternalId()));
		entity.setUpdatedAt(update.updatedAt());
		return productMapper.toDomain(productRepository.save(entity));
	}

	@Override
	public Product setActive(UUID externalId, boolean active, Instant updatedAt) {
		ProductJpaEntity entity = requireProduct(externalId);
		entity.setActive(active);
		entity.setUpdatedAt(updatedAt);
		return productMapper.toDomain(productRepository.save(entity));
	}

	@Override
	public Product setBaseUnit(UUID externalId, String baseUnit, Instant updatedAt) {
		ProductJpaEntity entity = requireProduct(externalId);
		entity.setBaseUnit(baseUnit);
		entity.setUpdatedAt(updatedAt);
		return productMapper.toDomain(productRepository.save(entity));
	}

	@Override
	public ProductPage list(ProductFilter filter) {
		Boolean active = switch (filter.active()) {
			case ACTIVE -> Boolean.TRUE;
			case INACTIVE -> Boolean.FALSE;
			case ALL -> null;
		};
		String q = filter.q() == null ? null : "%" + filter.q().toLowerCase(Locale.ROOT) + "%";
		String property = switch (filter.sort()) {
			case SKU -> "sku";
			case NAME -> "name";
			case CREATED_AT -> "createdAt";
		};
		Sort sort = Sort.by(filter.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, property);

		Page<ProductJpaEntity> page = productRepository.search(q, filter.categoryExternalId(), active,
				PageRequest.of(filter.page(), filter.size(), sort));
		List<ProductSummary> content = page.getContent().stream().map(productMapper::toSummary).toList();
		return new ProductPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	private ProductJpaEntity requireProduct(UUID externalId) {
		return productRepository.findByExternalIdWithUnits(externalId)
				.orElseThrow(() -> new IllegalStateException("No product found for external id " + externalId));
	}

	private CategoryJpaEntity requireCategory(UUID externalId) {
		return categoryRepository.findByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No category found for external id " + externalId));
	}
}
