package com.optiplant.inventory.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.sales.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.sales.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.sales.domain.exception.SaleNotFoundException;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.InvoiceNumber;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleItem;
import com.optiplant.inventory.sales.domain.model.SaleNotes;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaleAccessPolicyTest {

	private static final UUID BRANCH_A = UUID.randomUUID();
	private static final UUID BRANCH_B = UUID.randomUUID();

	@Test
	@DisplayName("R-02: Corporate ADMIN with null branchId cannot register a sale directly")
	void corporateAdminCannotRegisterSale() {
		AuthenticatedPrincipal corporateAdmin = new AuthenticatedPrincipal(
				UUID.randomUUID(), "admin", Role.ADMIN, null
		);

		assertThatThrownBy(() -> SaleAccessPolicy.resolveRegistrationBranch(corporateAdmin))
				.isInstanceOf(BranchContextRequiredException.class);
	}

	@Test
	@DisplayName("R-02: Branch user derives session branch for registration")
	void branchUserDerivesSessionBranch() {
		AuthenticatedPrincipal branchManager = new AuthenticatedPrincipal(
				UUID.randomUUID(), "manager", Role.BRANCH_MANAGER, BRANCH_A
		);

		UUID branch = SaleAccessPolicy.resolveRegistrationBranch(branchManager);
		assertThat(branch).isEqualTo(BRANCH_A);
	}

	@Test
	@DisplayName("R-25: Actor from branch A querying sale of branch B gets SaleNotFoundException (404, never 403)")
	void thirdBranchQueryGetsNotFound() {
		Sale saleInBranchB = sampleSale(BRANCH_B);
		AuthenticatedPrincipal userBranchA = new AuthenticatedPrincipal(
				UUID.randomUUID(), "seller", Role.OPERATOR, BRANCH_A
		);

		assertThatThrownBy(() -> SaleAccessPolicy.assertVisible(userBranchA, saleInBranchB))
				.isInstanceOf(SaleNotFoundException.class);
	}

	@Test
	@DisplayName("R-25: ADMIN can view sales from any branch")
	void adminCanViewAnyBranchSale() {
		Sale saleInBranchB = sampleSale(BRANCH_B);
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
				UUID.randomUUID(), "admin", Role.ADMIN, null
		);

		assertThatCode(() -> SaleAccessPolicy.assertVisible(admin, saleInBranchB))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("R-22 / §5: OPERATOR attempting to void own branch sale gets CrossBranchAccessDeniedException (403)")
	void operatorCannotVoid() {
		Sale saleInBranchA = sampleSale(BRANCH_A);
		AuthenticatedPrincipal operatorBranchA = new AuthenticatedPrincipal(
				UUID.randomUUID(), "operator", Role.OPERATOR, BRANCH_A
		);

		assertThatThrownBy(() -> SaleAccessPolicy.assertCanVoid(operatorBranchA, saleInBranchA))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
	}

	@Test
	@DisplayName("R-22 / §5: BRANCH_MANAGER from another branch attempting void gets SaleNotFoundException (404 before 403)")
	void crossBranchVoidGetsNotFoundFirst() {
		Sale saleInBranchB = sampleSale(BRANCH_B);
		AuthenticatedPrincipal managerBranchA = new AuthenticatedPrincipal(
				UUID.randomUUID(), "managerA", Role.BRANCH_MANAGER, BRANCH_A
		);

		assertThatThrownBy(() -> SaleAccessPolicy.assertCanVoid(managerBranchA, saleInBranchB))
				.isInstanceOf(SaleNotFoundException.class);
	}

	@Test
	@DisplayName("R-22 / §5: BRANCH_MANAGER on own branch sale can void")
	void managerCanVoidOwnBranchSale() {
		Sale saleInBranchA = sampleSale(BRANCH_A);
		AuthenticatedPrincipal managerBranchA = new AuthenticatedPrincipal(
				UUID.randomUUID(), "managerA", Role.BRANCH_MANAGER, BRANCH_A
		);

		assertThatCode(() -> SaleAccessPolicy.assertCanVoid(managerBranchA, saleInBranchA))
				.doesNotThrowAnyException();
	}

	private Sale sampleSale(UUID branchId) {
		SaleItem item = new SaleItem(
				UUID.randomUUID(),
				UUID.randomUUID(),
				SaleQuantity.of("1.0000"),
				Money.of("100.0000"),
				Money.of("100.0000"),
				DiscountPercent.ZERO,
				Money.of("100.0000")
		);
		SaleTotals totals = new SaleTotals(
				Money.of("100.0000"),
				Money.ZERO,
				Money.ZERO,
				Money.of("100.0000")
		);
		return new Sale(
				UUID.randomUUID(),
				InvoiceNumber.of("VEN-2026-0001"),
				SaleStatus.COMPLETED,
				branchId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				null,
				new CustomerName("Acme Corp"),
				null,
				totals,
				SaleNotes.empty(),
				Instant.now(),
				List.of(item)
		);
	}
}
