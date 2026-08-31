package com.optiplant.inventory.analytics.infrastructure.config;

import com.optiplant.inventory.analytics.infrastructure.config.ExternalAvailabilityApiKeyProperties.ApiKeyEntry;
import com.optiplant.inventory.analytics.infrastructure.config.ServiceUserPort.ServiceUserSubject;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter authenticating the external network availability path via {@code X-Api-Key} (CU-EXT-01, F-6, design §7, trap 5).
 *
 * <p>Compares in constant time, writes its own 401 JSON body (as it runs before DispatcherServlet),
 * and never logs key material (R-25, RNF-OBS-01). Guarded by {@link #shouldNotFilter} to apply only
 * to {@code /api/external/availability}.
 */
@Component
public class ExternalAvailabilityApiKeyFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-Api-Key";
	private static final String INVALID_CREDENTIAL_BODY = "{\"code\":\"invalid_api_credential\",\"message\":\"Invalid API credential\"}";

	private final ExternalAvailabilityApiKeyProperties properties;
	private final ServiceUserPort serviceUserPort;

	public ExternalAvailabilityApiKeyFilter(ExternalAvailabilityApiKeyProperties properties,
			ServiceUserPort serviceUserPort) {
		this.properties = properties;
		this.serviceUserPort = serviceUserPort;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/external/availability");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(API_KEY_HEADER);
		if (apiKey == null || apiKey.isBlank()) {
			writeUnauthorized(response);
			return;
		}

		ApiKeyEntry entry = properties.findMatchingEntry(apiKey);
		if (entry == null) {
			writeUnauthorized(response);
			return;
		}

		ServiceUserSubject subject = serviceUserPort.findActiveServiceUser(entry.userExternalId()).orElse(null);
		if (subject == null) {
			writeUnauthorized(response);
			return;
		}

		AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
				subject.userExternalId(),
				subject.username(),
				subject.role(),
				null
		);

		ExternalAvailabilityAuthenticationToken auth = new ExternalAvailabilityAuthenticationToken(principal);
		SecurityContextHolder.getContext().setAuthentication(auth);

		filterChain.doFilter(request, response);
	}

	private void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(INVALID_CREDENTIAL_BODY);
	}
}
