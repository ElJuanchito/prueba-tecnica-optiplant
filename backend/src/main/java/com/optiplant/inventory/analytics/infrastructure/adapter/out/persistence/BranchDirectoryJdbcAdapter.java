package com.optiplant.inventory.analytics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.analytics.application.port.out.BranchDirectoryPort;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Native-SQL read adapter for checking active branch existence (design §4 Q-8, §8).
 */
@Component
public class BranchDirectoryJdbcAdapter implements BranchDirectoryPort {

	private final JdbcClient jdbcClient;

	public BranchDirectoryJdbcAdapter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public boolean isActiveBranch(UUID branchExternalId) {
		if (branchExternalId == null) {
			return false;
		}
		String sql = "SELECT COUNT(*) FROM branches WHERE external_id = :branchExternalId AND is_active = TRUE";
		Long count = jdbcClient.sql(sql)
				.param("branchExternalId", branchExternalId)
				.query(Long.class)
				.single();
		return count != null && count > 0;
	}
}
