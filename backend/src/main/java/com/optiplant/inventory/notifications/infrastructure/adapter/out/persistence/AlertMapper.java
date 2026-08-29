package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.notifications.domain.model.Alert;
import java.util.UUID;
import org.mapstruct.Mapper;

/**
 * Entity ↔ domain mapping for {@code system_alerts}. {@code branchExternalId} and
 * {@code resolvedByUserExternalId} are supplied as extra source parameters, resolved by
 * {@link AlertForeignKeyResolverSpringDataRepository} — the entity only carries the plain
 * {@code Long} foreign keys.
 */
@Mapper(componentModel = "spring")
public interface AlertMapper {

	Alert toDomain(SystemAlertJpaEntity entity, UUID branchExternalId, UUID resolvedByUserExternalId);
}
