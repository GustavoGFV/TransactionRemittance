package com.inter.remittance.remittance.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current balances of a user")
public record BalanceResponse(
		@Schema(description = "Public user id", example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b") UUID userId,
		@Schema(description = "BRL balance", example = "1500.00") BigDecimal balanceBrl,
		@Schema(description = "USD balance", example = "250.50") BigDecimal balanceUsd) {
}
