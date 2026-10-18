package com.inter.remittance.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to update a user profile (document and balances are not editable)")
public record UpdateUserRequest(
		@Schema(description = "Full name or company name", example = "Maria Souza", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150)
		@NotBlank @Size(max = 150) String name,
		@Schema(description = "Unique email address", example = "maria.souza@mail.com", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254)
		@NotBlank @Email @Size(max = 254) String email) {
}
