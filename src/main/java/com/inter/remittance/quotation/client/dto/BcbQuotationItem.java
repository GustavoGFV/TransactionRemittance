package com.inter.remittance.quotation.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BcbQuotationItem(BigDecimal cotacaoCompra, BigDecimal cotacaoVenda, String dataHoraCotacao) {
}
