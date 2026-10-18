package com.inter.remittance.remittance.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a completed remittance")
public record RemittanceResponse(
		@Schema(description = "Operation performed", example = "BUY") RemittanceType type,
		@Schema(description = "Public id of the user whose BRL/USD balances are reported", example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b") UUID userId,
		@Schema(description = "Public id of the user that received the USD (the sender on same-account and reverse)", example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b") UUID beneficiaryId,
		@Schema(description = "Amount in BRL", example = "500.00") BigDecimal amountBrl,
		@Schema(description = "Amount in USD", example = "96.02") BigDecimal amountUsd,
		@Schema(description = "Buy quotation applied (cotacaoCompra)", example = "5.2073") BigDecimal quotation,
		@Schema(description = "BRL balance of the user after the operation", example = "1000.00") BigDecimal balanceBrl,
		@Schema(description = "USD balance of the user after the operation", example = "346.52") BigDecimal balanceUsd,
		@Schema(description = "BRL already used today against the daily limit (buy operations)", example = "500.00", nullable = true) BigDecimal dailyReachedAmount,
		@Schema(description = "Daily limit in BRL for the sender (buy operations)", example = "10000.00", nullable = true) BigDecimal dailyLimit) {
}
