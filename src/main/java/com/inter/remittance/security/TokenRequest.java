package com.inter.remittance.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials of the API client requesting a token")
public record TokenRequest(
		@Schema(description = "Client id", example = "remittance-client", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String clientId,
		@Schema(description = "Client secret", example = "remittance-secret", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String clientSecret) {
}
