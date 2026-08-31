package com.optiplant.inventory.iam.infrastructure.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Access denied handler emitting the uniform {@code { "code": "forbidden", "message": ... }} error envelope
 * for Spring Security authorization denials (contract §7, design §6 D-9).
 */
@Component
public class IamAccessDeniedHandler implements AccessDeniedHandler {

	private static final String FORBIDDEN_BODY = "{\"code\":\"forbidden\",\"message\":\"Access denied\"}";

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException, ServletException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(FORBIDDEN_BODY);
	}
}
