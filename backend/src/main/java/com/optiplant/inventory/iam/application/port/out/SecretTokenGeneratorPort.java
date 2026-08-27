package com.optiplant.inventory.iam.application.port.out;

/** Generates the raw, high-entropy secret handed to the client as a refresh token. */
public interface SecretTokenGeneratorPort {

	String generate();
}
