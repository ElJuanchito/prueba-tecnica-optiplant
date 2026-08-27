package com.optiplant.inventory.iam.application.port.out;

public interface PasswordHasherPort {

	boolean matches(String rawPassword, String hashedPassword);
}
