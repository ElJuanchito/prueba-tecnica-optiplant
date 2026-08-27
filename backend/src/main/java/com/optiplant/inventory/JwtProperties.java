package com.optiplant.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Clave de firma de los tokens. Vive en el paquete base porque todo subpaquete
 * directo sería tratado como un módulo por la regla de fronteras; migra a
 * {@code iam/infrastructure/config} cuando ese módulo se construya.
 *
 * <p>La validación corre al crear el contexto: en el perfil {@code prod} no hay
 * valor por defecto, de modo que la ausencia de {@code JWT_SECRET} rompe el
 * arranque en lugar de aparecer en el primer inicio de sesión.
 */
@Validated
@ConfigurationProperties(prefix = "optiplant.jwt")
public record JwtProperties(

		/**
		 * HMAC-SHA256 exige una clave de al menos 256 bits. Sin el mínimo, una clave
		 * corta se aceptaría en el arranque y fallaría al firmar.
		 */
		@NotBlank(message = "optiplant.jwt.secret es obligatorio")
		@Size(min = 32, message = "optiplant.jwt.secret requiere al menos 32 caracteres (256 bits) para HMAC-SHA256")
		String secret) {
}
