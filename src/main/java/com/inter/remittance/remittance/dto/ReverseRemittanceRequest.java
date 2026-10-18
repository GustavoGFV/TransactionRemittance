package com.inter.remittance.remittance.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.inter.remittance.user.validation.ValidDocument;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Convert the user's USD back to BRL, credited to the user's own account or to another registered user")
public record ReverseRemittanceRequest(
		@Schema(description = "Public id (UUID) of the user whose USD balance is debited", example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull UUID userId,
		@Schema(description = "Amount to convert, in USD (minimum 0.01)", example = "100.00",
				requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0.01")
		@NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amountUsd,
		@Schema(description = "CPF/CNPJ of the registered user who receives the BRL. Optional: when omitted, the BRL is credited to the user's own account",
				example = "11.222.333/0001-81", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
		@ValidDocument String recipientDocument) {

	public ReverseRemittanceRequest(UUID userId, BigDecimal amountUsd) {
		this(userId, amountUsd, null);
	}

}
