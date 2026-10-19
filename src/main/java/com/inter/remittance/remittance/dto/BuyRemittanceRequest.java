package com.inter.remittance.remittance.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.inter.remittance.user.validation.ValidDocument;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Buy USD with BRL, either into the user's own USD account or to another user")
public record BuyRemittanceRequest(
		@Schema(description = "Public id (UUID) of the sender, whose BRL balance is debited",
				example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull UUID userId,
		@Schema(description = "Amount to convert, in BRL (minimum 1.00)", example = "500.00",
				requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1.00")
		@NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amountBrl,
		@Schema(description = "true: credit the sender's own USD account; "
				+ "false: external transfer to the recipient. The daily limit applies to both",
				example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull Boolean isSameAccount,
		@Schema(description = "**OPTIONAL.** CPF/CNPJ of the recipient. Required only when isSameAccount is false; ignored otherwise",
				example = "11.222.333/0001-81", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
		@ValidDocument String recipientDocument) {
}
