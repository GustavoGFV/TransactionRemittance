package com.inter.remittance.quotation.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BcbQuotationResponse(List<BcbQuotationItem> value) {
}
