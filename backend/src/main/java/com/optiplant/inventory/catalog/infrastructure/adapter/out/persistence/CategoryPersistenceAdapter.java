package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.CategorySummary;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The single {@link CategoryRepositoryPort} implementation and the only class in
 * {@code catalog} that touches a category's internal numeric {@code id} — it
 * resolves the id purely to drive the product-side counts and never lets it
 * cross back out: every returned value is an {@code external_id} UUID or a domain
 * record (design §6.2).
 *
 * <p>{@link #list} issues exactly two statements regardless of page size — the
 * page query, then one grouped count over the page's category ids — never one
 * count per row (design §6.2, task 3.4).
 */
@Component
public class CategoryPersistenceAdapter implements CategoryRepositoryPort {

	private final CategorySpringDataRepository categoryRepository;
	private final CategoryMapper categoryMapper;

	public CategoryPersistenceAdapter(CategorySpringDataRepository categoryRepository, CategoryMapper categoryMapper) {
		this.categoryRepository = categoryRepository;
		this.categoryMapper = categoryMapper;
	}

	@Override
	public Optional<CategorySummary> findByExternalId(UUID externalId) {
		return categoryRepository.findByExternalId(externalId).map(this::toSummary);
	}

	@Override
	public Optional<CategoryRef> findRefByExternalId(UUID externalId) {
		return categoryRepository.findByExternalId(externalId).map(categoryMapper::toRef);
	}

	@Override
	public boolean existsByNameIgnoringCase(String comparisonKey, UUID excludingExternalId) {
		return categoryRepository.existsByNameIgnoringCase(comparisonKey, excludingExternalId);
	}

	@Override
	public boolean hasActiveProducts(UUID externalId) {
		return categoryRepository.hasActiveProducts(externalId);
	}

	@Override
	public CategorySummary create(NewCategory newCategory) {
		CategoryJpaEntity entity = new CategoryJpaEntity();
		entity.setName(newCategory.name());
		entity.setDescription(newCategory.description());
		entity.setActive(true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return toSummary(categoryRepository.save(entity));
	}

	@Override
	public CategorySummary update(UUID externalId, CategoryUpdate update) {
		CategoryJpaEntity entity = categoryRepository.findByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No category found for external id " + externalId));
		entity.setName(update.name());
		entity.setDescription(update.description());
		entity.setUpdatedAt(update.updatedAt());
		return toSummary(categoryRepository.save(entity));
	}

	@Override
	public CategorySummary setActive(UUID externalId, boolean active, Instant updatedAt) {
		CategoryJpaEntity entity = categoryRepository.findByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No category found for external id " + externalId));
		entity.setActive(active);
		entity.setUpdatedAt(updatedAt);
		return toSummary(categoryRepository.save(entity));
	}

	@Override
	public CategoryPage list(CategoryFilter filter) {
		Boolean active = switch (filter.active()) {
			case ACTIVE -> Boolean.TRUE;
			case INACTIVE -> Boolean.FALSE;
			case ALL -> null;
		};
		String namePattern = filter.name() == null ? null
				: "%" + filter.name().toLowerCase(Locale.ROOT) + "%";
		Page<CategoryJpaEntity> page = categoryRepository.search(namePattern, active,
				PageRequest.of(filter.page(), filter.size()));

		Map<Long, Long> counts = activeProductCounts(page.getContent().stream().map(CategoryJpaEntity::getId).toList());
		List<CategorySummary> content = page.getContent().stream()
				.map(entity -> new CategorySummary(categoryMapper.toDomain(entity),
						counts.getOrDefault(entity.getId(), 0L)))
				.toList();
		return new CategoryPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	private CategorySummary toSummary(CategoryJpaEntity entity) {
		long count = activeProductCounts(List.of(entity.getId())).getOrDefault(entity.getId(), 0L);
		return new CategorySummary(categoryMapper.toDomain(entity), count);
	}

	private Map<Long, Long> activeProductCounts(List<Long> categoryIds) {
		if (categoryIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> counts = new HashMap<>();
		for (Object[] row : categoryRepository.countActiveProductsByCategoryIds(categoryIds)) {
			counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
		}
		return counts;
	}
}
