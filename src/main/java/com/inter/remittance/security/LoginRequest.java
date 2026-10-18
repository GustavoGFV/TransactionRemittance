package com.inter.remittance.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "E-mail and password of a registered user")
public record LoginRequest(
		@Schema(description = "User e-mail", example = "maria@mail.com", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String email,
		@Schema(description = "User password", example = "S3nh@Forte2026", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String password) {

	@Override
	public String toString() {
		return "LoginRequest[email=" + email + "]";
	}

}
