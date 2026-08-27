package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Slice 2a only needs {@code save}; lookup-by-hash and revocation are added when
 * slice 2b's {@code SessionRefreshService} needs to rotate a presented token. */
public interface RefreshTokenSpringDataRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {
}
