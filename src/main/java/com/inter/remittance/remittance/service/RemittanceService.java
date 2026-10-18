package com.inter.remittance.remittance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.inter.remittance.quotation.dto.BuyQuotation;
import com.inter.remittance.quotation.service.QuotationService;
import com.inter.remittance.remittance.dto.BalanceResponse;
import com.inter.remittance.remittance.dto.BuyRemittanceRequest;
import com.inter.remittance.remittance.dto.PreviewResponse;
import com.inter.remittance.remittance.dto.RemittanceResponse;
import com.inter.remittance.remittance.dto.ReverseRemittanceRequest;

/**
 * Orchestrates the {@code quotation} and {@code user} packages. Work runs on virtual threads and the
 * quotation is resolved first (outside any transaction); only then the short DB transaction is opened.
 * This service handles remittance operations, including balance inquiries, previews, purchases, and reversals.
 * Provides asynchronous methods for interacting with the remittance system.
 * All operations are performed asynchronously to improve responsiveness and scalability.
 * @see QuotationService
 * @see RemittanceTransactionService
 */
@Service
public class RemittanceService {

	private static final int MONEY_SCALE = 2;

	private final QuotationService quotationService;
	private final RemittanceTransactionService transactionService;
	private final Executor executor;
	private final Clock clock;

	public RemittanceService(QuotationService quotationService, RemittanceTransactionService transactionService,
			@Qualifier("quotationExecutor") Executor executor, Clock clock) {
		this.quotationService = quotationService;
		this.transactionService = transactionService;
		this.executor = executor;
		this.clock = clock;
	}

	public CompletableFuture<BalanceResponse> balance(UUID userId) {
		return CompletableFuture.supplyAsync(() -> transactionService.balance(userId), executor);
	}

	/**
	 * Endpoint to preview a remittance transaction for a specific user and amount in USD.
	 * 
	 * @param userId the ID of the user
	 * @param amountUsd the amount in USD to preview
	 * @return a CompletableFuture containing the preview response
	 */
	public CompletableFuture<PreviewResponse> preview(UUID userId, BigDecimal amountUsd) {
		return CompletableFuture.runAsync(() -> transactionService.requireUser(userId), executor)
				.thenCompose(ignored -> currentBuyQuotation())
				.thenApply(quotation -> new PreviewResponse(userId, amountUsd, quotation,
						amountUsd.multiply(quotation).setScale(MONEY_SCALE, RoundingMode.UP)));
	}

	public CompletableFuture<RemittanceResponse> buy(BuyRemittanceRequest request) {
		return currentBuyQuotation().thenApplyAsync(quotation -> transactionService.buy(request, quotation), executor);
	}

	public CompletableFuture<RemittanceResponse> reverse(ReverseRemittanceRequest request) {
		return currentBuyQuotation().thenApplyAsync(quotation -> transactionService.reverse(request, quotation), executor);
	}

	/**
	 * Retrieves the current buy quotation for the day.
	 * 
	 * @return a CompletableFuture containing the latest buy quotation
	 */
	private CompletableFuture<BigDecimal> currentBuyQuotation() {
		return quotationService.findBuyQuotations(LocalDate.now(clock))
				.thenApply(RemittanceService::lastBuyQuotation);
	}

	private static BigDecimal lastBuyQuotation(List<BuyQuotation> quotations) {
		return quotations.getLast().cotacaoCompra();
	}

}
