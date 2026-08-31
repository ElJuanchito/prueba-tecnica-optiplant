package com.optiplant.inventory.analytics.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration-supplied API keys for external network availability queries (CU-EXT-01, F-6, PA-05, design §7).
 * Unlike sales, CU-EXT-01 is network-wide (R-24), so entries carry no {@code branchExternalId}.
 */
@ConfigurationProperties(prefix = "optiplant.analytics.external")
public record ExternalAvailabilityApiKeyProperties(
		List<ApiKeyEntry> apiKeys
) {

	public record ApiKeyEntry(
			String key,
			UUID userExternalId
	) {
	}

	/**
	 * Finds a matching entry in constant time (MessageDigest.isEqual over UTF-8 bytes).
	 */
	public ApiKeyEntry findMatchingEntry(String candidateKey) {
		if (candidateKey == null || apiKeys == null) {
			return null;
		}
		byte[] candidateBytes = candidateKey.getBytes(StandardCharsets.UTF_8);
		ApiKeyEntry matched = null;
		for (ApiKeyEntry entry : apiKeys) {
			if (entry.key() != null) {
				byte[] entryBytes = entry.key().getBytes(StandardCharsets.UTF_8);
				if (MessageDigest.isEqual(entryBytes, candidateBytes)) {
					matched = entry;
				}
			}
		}
		return matched;
	}
}
