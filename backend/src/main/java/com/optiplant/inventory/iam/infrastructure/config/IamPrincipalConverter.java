package com.optiplant.inventory.iam.infrastructure.config;

import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps a validated {@link Jwt}'s claims into {@code Authentication{principal =
 * shared.AuthenticatedPrincipal, authorities = [SimpleGrantedAuthority(role.name())]}},
 * per design's AUTHENTICATED REQUEST data flow. Registered on {@code
 * OAuth2ResourceServerConfigurer.JwtConfigurer#jwtAuthenticationConverter} in
 * {@code SecurityConfig}.
 */
public class IamPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		Role role = Role.valueOf(jwt.getClaimAsString("role"));
		String branchClaim = jwt.getClaimAsString("branch_id");
		UUID branchId = branchClaim != null ? UUID.fromString(branchClaim) : null;
		String username = jwt.getClaimAsString("username");

		AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, username, role, branchId);
		return new IamAuthenticationToken(principal, Set.of(new SimpleGrantedAuthority(role.name())));
	}
}
