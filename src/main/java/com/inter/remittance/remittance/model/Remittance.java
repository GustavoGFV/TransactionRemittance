package com.inter.remittance.remittance.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An international remittance")
public record Remittance(
		@Schema(description = "Remittance id", example = "1") Long id,
		@Schema(description = "Amount sent, in USD", example = "100.00") BigDecimal amountInDollars,
		@Schema(description = "Buy quotation applied", example = "5.2073") BigDecimal buyQuotation,
		@Schema(description = "Remittance date", example = "2026-10-01") LocalDate date) {
}
