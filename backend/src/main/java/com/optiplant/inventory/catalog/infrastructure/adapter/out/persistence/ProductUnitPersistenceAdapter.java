package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.catalog.application.port.out.ProductUnitRepositoryPort;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single {@link ProductUnitRepositoryPort} implementation and the class that
 * keeps S-3 satisfiable. Every method that can leave a row with
 * {@code is_default_sale_unit = TRUE} — {@link #add} and {@link #replace} — runs
 * the two steps of design §8.2 in this order and never the reverse:
 *
 * <ol>
 *   <li><strong>Clear.</strong> Call {@link ProductUnitSpringDataRepository#clearDefaultSaleUnit},
 *       which is {@code @Modifying(flushAutomatically = true, clearAutomatically = true)}:
 *       pending changes are flushed before it runs and the persistence context is
 *       cleared after, so the {@code UPDATE ... SET FALSE} reaches the database ahead
 *       of anything that follows and no stale managed entity survives to overwrite it.</li>
 *   <li><strong>Set.</strong> Only then insert / update the row that carries
 *       {@code is_default_sale_unit = TRUE}.</li>
 * </ol>
 *
 * <p>Both steps are skipped when the incoming unit carries {@code defaultSaleUnit =
 * false}: there is nothing to clear, and clearing would wrongly unset an unrelated
 * sibling. The inline-units path of {@link ProductPersistenceAdapter#create} needs
 * no clearing step at all — see its Javadoc.
 *
 * <p>Proven by {@code ProductUnitCatalogIT} against real PostgreSQL (task 6.9): a
 * unit test cannot observe a flush ordering that only exists when a real database
 * checks a real partial unique index per statement.
 */
@Component
public class ProductUnitPersistenceAdapter implements ProductUnitRepositoryPort {

	private final ProductUnitSpringDataRepository unitRepository;
	private final ProductSpringDataRepository productRepository;
	private final ProductMapper productMapper;

	public ProductUnitPersistenceAdapter(ProductUnitSpringDataRepository unitRepository,
			ProductSpringDataRepository productRepository, ProductMapper productMapper) {
		this.unitRepository = unitRepository;
		this.productRepository = productRepository;
		this.productMapper = productMapper;
	}

	@Override
	public List<ProductUnit> findByProduct(UUID productExternalId) {
		return unitRepository.findByProductExternalId(productExternalId).stream()
				.map(productMapper::toUnit)
				.toList();
	}

	@Override
	public Optional<ProductUnit> find(UUID productExternalId, UUID unitExternalId) {
		return unitRepository.findScoped(productExternalId, unitExternalId).map(productMapper::toUnit);
	}

	@Override
	public void clearDefaultSaleUnit(UUID productExternalId) {
		unitRepository.clearDefaultSaleUnit(productExternalId);
	}

	@Override
	public ProductUnit add(UUID productExternalId, NewUnitRow unit) {
		// §8.2 step 1 — clear the previous default in a flushed statement BEFORE
		// step 2 inserts the row that ends is_default_sale_unit = TRUE. Skipped
		// when the incoming unit is not a default. The product is loaded AFTER the
		// clear so it comes back managed (clearAutomatically detaches everything).
		if (unit.defaultSaleUnit()) {
			unitRepository.clearDefaultSaleUnit(productExternalId);
		}
		ProductJpaEntity product = requireProduct(productExternalId);

		ProductUnitJpaEntity entity = new ProductUnitJpaEntity();
		entity.setProduct(product);
		entity.setUnitName(unit.unitName());
		entity.setConversionFactor(unit.conversionFactor());
		entity.setDefaultSaleUnit(unit.defaultSaleUnit());
		entity.setCreatedAt(Instant.now());
		return productMapper.toUnit(unitRepository.save(entity));
	}

	@Override
	public ProductUnit replace(UUID productExternalId, UUID unitExternalId, NewUnitRow unit) {
		// Load first so a not-found unit fails before any clearing happens.
		requireUnit(productExternalId, unitExternalId);

		// §8.2 step 1 — clear, then re-load (clearAutomatically detached the entity).
		if (unit.defaultSaleUnit()) {
			unitRepository.clearDefaultSaleUnit(productExternalId);
		}
		ProductUnitJpaEntity entity = requireUnit(productExternalId, unitExternalId);

		// §8.2 step 2 — the row that ends is_default_sale_unit = TRUE.
		entity.setUnitName(unit.unitName());
		entity.setConversionFactor(unit.conversionFactor());
		entity.setDefaultSaleUnit(unit.defaultSaleUnit());
		return productMapper.toUnit(unitRepository.save(entity));
	}

	@Override
	public void delete(UUID productExternalId, UUID unitExternalId) {
		// Bulk delete: the service has already loaded the parent product with its
		// managed units collection, so em.remove would be reconciled away (see
		// ProductUnitSpringDataRepository#deleteScoped). Existence is validated by
		// the service through ProductUnitRepositoryPort#find before this call.
		unitRepository.deleteScoped(productExternalId, unitExternalId);
	}

	private ProductJpaEntity requireProduct(UUID productExternalId) {
		return productRepository.findByExternalIdWithUnits(productExternalId)
				.orElseThrow(() -> new IllegalStateException("No product found for external id " + productExternalId));
	}

	private ProductUnitJpaEntity requireUnit(UUID productExternalId, UUID unitExternalId) {
		return unitRepository.findScoped(productExternalId, unitExternalId)
				.orElseThrow(() -> new IllegalStateException(
						"No product unit " + unitExternalId + " for product " + productExternalId));
	}
}
