package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code suppliers} ({@code 01-init-schema.sql:248-260}, design §6.1). Corporate data —
 * no {@code branch_id} column (R-02).
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class SupplierJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "tax_id", nullable = false, unique = true, length = 30)
	private String taxId;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "contact_name", length = 100)
	private String contactName;

	@Column(name = "email", length = 100)
	private String email;

	@Column(name = "phone", length = 50)
	private String phone;

	@Column(name = "address", length = 255)
	private String address;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();
}
