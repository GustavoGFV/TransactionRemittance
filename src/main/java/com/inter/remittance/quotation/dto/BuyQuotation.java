package com.inter.remittance.quotation.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "BRL price of one USD when buying, as published by the BCB")
public record BuyQuotation(
		@Schema(description = "Buy quotation (cotacaoCompra)", example = "5.2073") BigDecimal cotacaoCompra) {
}
