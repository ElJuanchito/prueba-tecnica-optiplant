package com.optiplant.inventory.sales.infrastructure.config;

import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.ServiceUserSubject;
import com.optiplant.inventory.sales.infrastructure.config.ExternalApiKeyProperties.ApiKeyEntry;
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
 * Filter authenticating the POS path via {@code X-Api-Key} (F-6, design §6.5, R-28).
 *
 * <p>Compares in constant time, writes its own 401 JSON body (as it runs before DispatcherServlet),
 * and never logs key material (RNF-OBS-01).
 */
@Component
public class ExternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-Api-Key";
	private static final String INVALID_CREDENTIAL_BODY = "{\"code\":\"invalid_api_credential\",\"message\":\"Invalid API credential\"}";

	private final ExternalApiKeyProperties properties;
	private final SaleReferencePort referencePort;

	public ExternalApiKeyAuthenticationFilter(ExternalApiKeyProperties properties, SaleReferencePort referencePort) {
		this.properties = properties;
		this.referencePort = referencePort;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/external/sales");
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

		ServiceUserSubject subject = referencePort.findExternalCredentialSubject(entry.userExternalId()).orElse(null);
		if (subject == null) {
			writeUnauthorized(response);
			return;
		}

		AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
				subject.userExternalId(),
				subject.username(),
				subject.role(),
				entry.branchExternalId()
		);

		ExternalSalesAuthenticationToken auth = new ExternalSalesAuthenticationToken(principal);
		SecurityContextHolder.getContext().setAuthentication(auth);

		filterChain.doFilter(request, response);
	}

	private void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(INVALID_CREDENTIAL_BODY);
	}
}
