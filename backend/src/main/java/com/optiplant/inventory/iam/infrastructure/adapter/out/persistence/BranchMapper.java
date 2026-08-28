package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.domain.model.BranchProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BranchMapper {

	BranchProfile toDomain(BranchJpaEntity entity);
}
