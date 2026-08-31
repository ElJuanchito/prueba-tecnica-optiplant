package com.optiplant.inventory.analytics.application.port.out;

import java.util.UUID;

/**
 * Secondary read port for checking active branch existence (design §4 Q-8, §8).
 */
public interface BranchDirectoryPort {

	boolean isActiveBranch(UUID branchExternalId);
}
