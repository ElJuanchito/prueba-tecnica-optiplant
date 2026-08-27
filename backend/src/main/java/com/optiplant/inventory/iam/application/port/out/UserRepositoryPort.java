package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.iam.domain.model.UserAccount;
import java.util.Optional;

public interface UserRepositoryPort {

	Optional<UserAccount> findByUsername(String username);
}
