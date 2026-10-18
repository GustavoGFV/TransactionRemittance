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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
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
	@Operation(summary = "Get dollar buy quotation",
			description = "Returns the buy quotation (cotacaoCompra) for each PTAX bulletin of the given day. "
					+ "Weekends and holidays have no bulletin, so the last business day before the date is used.")
	@ApiResponse(responseCode = "200", description = "Buy quotations of the day (or of the last business day)")
	@ApiResponse(responseCode = "400", description = "Missing or malformed date",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No quotation found in the recent business days",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "BCB API unavailable (circuit breaker open or retries exhausted)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public CompletableFuture<List<BuyQuotation>> getBuyQuotations(
			@Parameter(description = "Quotation date in ISO format (yyyy-MM-dd)", required = true, example = "2026-10-01")
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return quotationService.findBuyQuotations(date);
	}

}
