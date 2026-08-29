package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.inventory.application.port.out.ProductLookupPort;
import com.optiplant.inventory.inventory.domain.model.ProductDescriptor;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.ForeignKeyResolverSpringDataRepository.ProductDescriptorRow;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The single {@link ProductLookupPort} implementation, resolving {@code catalog}'s
 * {@code products} table through native projections — no {@code @Entity} spans the module
 * boundary (design §5.2, §6.1).
 */
@Component
public class ProductLookupAdapter implements ProductLookupPort {

	private final ForeignKeyResolverSpringDataRepository foreignKeyResolver;

	public ProductLookupAdapter(ForeignKeyResolverSpringDataRepository foreignKeyResolver) {
		this.foreignKeyResolver = foreignKeyResolver;
	}

	@Override
	public Optional<ProductDescriptor> findByExternalId(UUID productExternalId) {
		return foreignKeyResolver.findProductDescriptor(productExternalId).map(ProductLookupAdapter::toDescriptor);
	}

	@Override
	public Map<UUID, ProductDescriptor> findAllByExternalIds(Collection<UUID> productExternalIds) {
		if (productExternalIds.isEmpty()) {
			return Map.of();
		}
		return foreignKeyResolver.findProductDescriptors(productExternalIds).stream()
				.collect(Collectors.toMap(ProductDescriptorRow::getExternalId, ProductLookupAdapter::toDescriptor));
	}

	private static ProductDescriptor toDescriptor(ProductDescriptorRow row) {
		return new ProductDescriptor(row.getExternalId(), row.getSku(), row.getName());
	}
}
