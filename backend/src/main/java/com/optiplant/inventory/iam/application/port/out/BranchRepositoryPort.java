package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.iam.domain.model.BranchProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepositoryPort {

	Optional<BranchProfile> findByCode(String code);

	Optional<BranchProfile> findByExternalId(UUID externalId);

	BranchProfile create(NewBranch newBranch);

	BranchProfile update(UUID externalId, BranchUpdate update);

	void disable(UUID externalId);

	BranchPage list(BranchFilter filter);

	record NewBranch(String code, String name, String address, String city, String phone) {
	}

	/** {@code code} and {@code external_id} are deliberately absent — edit never changes either. */
	record BranchUpdate(String name, String address, String city, String phone) {
	}

	record BranchFilter(Boolean active, int page, int size) {
	}

	record BranchPage(List<BranchProfile> content, long totalElements, int page, int size) {
	}
}
