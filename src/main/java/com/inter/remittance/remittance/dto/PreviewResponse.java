package com.inter.remittance.remittance.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How much a USD amount costs in BRL at the current buy quotation")
public record PreviewResponse(
		@Schema(description = "Public user id", example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b") UUID userId,
		@Schema(description = "Desired amount in USD", example = "100.00") BigDecimal amountUsd,
		@Schema(description = "Buy quotation applied (cotacaoCompra)", example = "5.2073") BigDecimal quotation,
		@Schema(description = "Cost in BRL (rounded up to the cent)", example = "520.73") BigDecimal amountBrl) {
}
