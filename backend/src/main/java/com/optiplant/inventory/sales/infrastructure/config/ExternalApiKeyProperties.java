package com.optiplant.inventory.sales.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration-supplied API keys for the external POS intake (F-6, design §6.5).
 */
@ConfigurationProperties(prefix = "optiplant.sales.external")
public record ExternalApiKeyProperties(
		List<ApiKeyEntry> apiKeys
) {

	public record ApiKeyEntry(
			String key,
			UUID branchExternalId,
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
