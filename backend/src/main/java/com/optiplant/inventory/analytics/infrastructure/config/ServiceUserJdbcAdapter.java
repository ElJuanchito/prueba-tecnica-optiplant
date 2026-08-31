package com.optiplant.inventory.analytics.infrastructure.config;

import com.optiplant.inventory.shared.security.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Native-SQL adapter resolving active service users from {@code users} via {@link JdbcClient} (design §4 Q-9).
 */
@Component
public class ServiceUserJdbcAdapter implements ServiceUserPort {

	private final JdbcClient jdbcClient;

	public ServiceUserJdbcAdapter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Optional<ServiceUserSubject> findActiveServiceUser(UUID userExternalId) {
		if (userExternalId == null) {
			return Optional.empty();
		}
		String sql = "SELECT external_id, username, role FROM users WHERE external_id = :userExternalId AND is_active = TRUE";
		return jdbcClient.sql(sql)
				.param("userExternalId", userExternalId)
				.query((rs, rowNum) -> new ServiceUserSubject(
						rs.getObject("external_id", UUID.class),
						rs.getString("username"),
						Role.valueOf(rs.getString("role"))
				))
				.optional();
	}
}
