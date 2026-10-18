package com.inter.remittance.user.dto;

import java.math.BigDecimal;

import com.inter.remittance.user.validation.ValidDocument;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to register a new user")
public record CreateUserRequest(
		@Schema(description = "Full name or company name", example = "Maria Silva", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150)
		@NotBlank @Size(max = 150) String name,
		@Schema(description = "Unique email address", example = "maria.silva@mail.com", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254)
		@NotBlank @Email @Size(max = 254) String email,
		@Schema(description = "Valid and unique CPF (11 digits) or CNPJ (14 characters); punctuation is accepted and removed",
				example = "529.982.247-25", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank @ValidDocument String document,
		@Schema(description = "Password, 8 to 72 characters. Stored as a BCrypt hash and never returned", example = "S3nh@Forte2026",
				requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72, accessMode = Schema.AccessMode.WRITE_ONLY)
		@NotBlank @Size(min = 8, max = 72) String password,
		@Schema(description = "Initial BRL balance (defaults to 0)", example = "1500.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED, minimum = "0")
		@PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal balanceBrl,
		@Schema(description = "Initial USD balance (defaults to 0)", example = "250.50", requiredMode = Schema.RequiredMode.NOT_REQUIRED, minimum = "0")
		@PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal balanceUsd) {

	// The password must never reach the logs
	@Override
	public String toString() {
		return "CreateUserRequest[name=" + name + ", email=" + email + ", document=" + document + "]";
	}
}
