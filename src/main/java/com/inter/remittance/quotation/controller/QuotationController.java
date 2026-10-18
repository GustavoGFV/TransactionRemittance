package com.inter.remittance.quotation.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inter.remittance.quotation.dto.BuyQuotation;
import com.inter.remittance.quotation.service.QuotationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/quotations")
@Tag(name = "Quotation", description = "Dollar quotations from the BCB PTAX API")
public class QuotationController {

	private final QuotationService quotationService;

	public QuotationController(QuotationService quotationService) {
		this.quotationService = quotationService;
	}

	@GetMapping
	@Operation(summary = "Get dollar buy quotation", description = "Returns the buy quotation (cotacaoCompra) for each PTAX bulletin of the given day")
	public CompletableFuture<List<BuyQuotation>> getBuyQuotations(
			@Parameter(description = "Quotation date (yyyy-MM-dd)", example = "2026-10-01")
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return quotationService.findBuyQuotations(date);
	}

}
