package com.optiplant.inventory.pricing.domain.service;

import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.PriceScope;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure domain resolution of prices under RN-16 and R-11 (design §3, §6.2).
 *
 * <p>Over candidate rows:
 * <ol>
 *   <li>Keep those whose {@code validity.coversAt(operationDate)}.</li>
 *   <li>Return the branch-scoped one if present, else the corporate one, else empty.</li>
 * </ol>
 */
public final class PriceResolutionPolicy {

	private PriceResolutionPolicy() {
	}

	/**
	 * Resolves the effective price for a single product from candidate rows.
	 *
	 * @param candidates candidate price rows
	 * @param operationDate operation date to check validity against
	 * @return resolved price, or {@link Optional#empty()} if no eligible price is valid
	 */
	public static Optional<Price> resolveForProduct(Collection<Price> candidates, LocalDate operationDate) {
		if (candidates == null || candidates.isEmpty() || operationDate == null) {
			return Optional.empty();
		}
		Price corporate = null;
		for (Price candidate : candidates) {
			if (!candidate.validity().coversAt(operationDate)) {
				continue;
			}
			if (candidate.scope() == PriceScope.BRANCH) {
				return Optional.of(candidate);
			}
			if (candidate.scope() == PriceScope.CORPORATE) {
				corporate = candidate;
			}
		}
		return Optional.ofNullable(corporate);
	}

	/**
	 * Resolves the effective price per product from candidate rows across multiple products.
	 *
	 * @param candidates candidate price rows
	 * @param operationDate operation date to check validity against
	 * @return map from product external ID to resolved {@link Price}
	 */
	public static Map<UUID, Price> resolveAll(Collection<Price> candidates, LocalDate operationDate) {
		if (candidates == null || candidates.isEmpty() || operationDate == null) {
			return Map.of();
		}
		Map<UUID, Map<PriceScope, Price>> byProduct = new HashMap<>();
		for (Price candidate : candidates) {
			if (!candidate.validity().coversAt(operationDate)) {
				continue;
			}
			byProduct.computeIfAbsent(candidate.productExternalId(), k -> new HashMap<>())
					.put(candidate.scope(), candidate);
		}
		Map<UUID, Price> resolved = new HashMap<>();
		for (Map.Entry<UUID, Map<PriceScope, Price>> entry : byProduct.entrySet()) {
			Map<PriceScope, Price> scopes = entry.getValue();
			if (scopes.containsKey(PriceScope.BRANCH)) {
				resolved.put(entry.getKey(), scopes.get(PriceScope.BRANCH));
			} else if (scopes.containsKey(PriceScope.CORPORATE)) {
				resolved.put(entry.getKey(), scopes.get(PriceScope.CORPORATE));
			}
		}
		return resolved;
	}
}
