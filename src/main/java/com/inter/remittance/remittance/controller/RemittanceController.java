package com.inter.remittance.remittance.controller;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inter.remittance.remittance.dto.BalanceResponse;
import com.inter.remittance.remittance.dto.BuyRemittanceRequest;
import com.inter.remittance.remittance.dto.PreviewResponse;
import com.inter.remittance.remittance.dto.RemittanceResponse;
import com.inter.remittance.remittance.dto.ReverseRemittanceRequest;
import com.inter.remittance.remittance.service.RemittanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

@RestController
@RequestMapping("/remittances")
@Tag(name = "Remittance", description = "BRL/USD balances, quotation preview and currency exchange (requires Bearer token)")
@SecurityRequirement(name = "bearerAuth")
public class RemittanceController {

	private static final String PROBLEM = "application/problem+json";
	private static final String USER_ID_EXAMPLE = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b";

	private final RemittanceService remittanceService;

	public RemittanceController(RemittanceService remittanceService) {
		this.remittanceService = remittanceService;
	}

	/**
	 * Endpoint to get the BRL and USD balances of a user.
	 * @param userId the public user id (UUID)
	 * @return the BRL and USD balances of the user
	 */
	@GetMapping("/users/{userId}/balance")
	@Operation(summary = "Get BRL and USD balances", description = "Returns both wallet balances of the user")
	@ApiResponse(responseCode = "200", description = "Balances of the user")
	@ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token", content = @Content)
	@ApiResponse(responseCode = "404", description = "User not found",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public CompletableFuture<BalanceResponse> balance(
			@Parameter(description = "Public user id (UUID)", required = true, example = USER_ID_EXAMPLE)
			@PathVariable UUID userId) {
		return remittanceService.balance(userId);
	}

	/**
	 * Endpoint to preview the BRL cost of a USD amount.
	 * @param userId the public user id (UUID)
	 * @param usdAmount the desired amount in USD
	 * @return the quotation and calculated cost in BRL
	 */
	@GetMapping("/users/{userId}/preview")
	@Operation(summary = "Preview the BRL cost of a USD amount",
			description = "Calculates how many Reais are needed to buy the desired USD amount at the current buy quotation (cotacaoCompra). Nothing is changed.")
	@ApiResponse(responseCode = "200", description = "Quotation and calculated cost")
	@ApiResponse(responseCode = "400", description = "Invalid amount",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token", content = @Content)
	@ApiResponse(responseCode = "404", description = "User or quotation not found",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "BCB API unavailable",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public CompletableFuture<PreviewResponse> preview(
			@Parameter(description = "Public user id (UUID)", required = true, example = USER_ID_EXAMPLE)
			@PathVariable UUID userId,
			@Parameter(description = "Desired amount in USD (minimum 0.01)", required = true, example = "100.00")
			@RequestParam @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal usdAmount) {
		return remittanceService.preview(userId, usdAmount);
	}

	/**
	 * Endpoint to buy USD with BRL.
	 * @param request the buy remittance request containing sender, recipient, and amount details
	 * @return the remittance response with transaction details
	 */
	@PostMapping("/buy")
	@Operation(summary = "Buy USD with BRL",
			description = "Debits the sender's BRL and credits USD at the current buy quotation. With isSameAccount=true the USD goes to the sender's own account; "
					+ "otherwise it goes to the user owning recipientDocument (PF and PJ can send to each other). Either way the amount counts toward the sender's daily limit (CPF: R$ 10,000, CNPJ: R$ 50,000). Atomic: any failure rolls everything back.")
	@ApiResponse(responseCode = "200", description = "Remittance completed")
	@ApiResponse(responseCode = "400", description = "Invalid payload or recipient document",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token", content = @Content)
	@ApiResponse(responseCode = "404", description = "Sender, recipient or quotation not found",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Insufficient BRL balance or daily limit exceeded",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "BCB API unavailable",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Sender, BRL amount and destination. recipientDocument is mandatory only when isSameAccount is false.",
			required = true, content = @Content(examples = {
					@io.swagger.v3.oas.annotations.media.ExampleObject(name = "Same account",
							value = "{\"userId\":\"3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b\",\"amountBrl\":500.00,\"isSameAccount\":true}"),
					@io.swagger.v3.oas.annotations.media.ExampleObject(name = "External transfer",
							value = "{\"userId\":\"3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b\",\"amountBrl\":500.00,\"isSameAccount\":false,\"recipientDocument\":\"11.222.333/0001-81\"}")}))
	public CompletableFuture<RemittanceResponse> buy(@Valid @RequestBody BuyRemittanceRequest request) {
		return remittanceService.buy(request);
	}

	/**
	 * Endpoint to reverse a remittance (USD to BRL).
	 * @param request the reverse remittance request containing sender, recipient, and amount details
	 * @return the remittance response with transaction details
	 */
	@PostMapping("/reverse")
	@Operation(summary = "Reverse remittance (USD to BRL)",
			description = "Debits USD and credits BRL on the user's own account using the current buy quotation (BRL amount rounded down to the cent). Does not count toward the daily limit.")
	@ApiResponse(responseCode = "200", description = "Conversion completed")
	@ApiResponse(responseCode = "400", description = "Invalid payload",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token", content = @Content)
	@ApiResponse(responseCode = "404", description = "User or quotation not found",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Insufficient USD balance",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "BCB API unavailable",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User and USD amount to convert; both fields are mandatory.", required = true,
			content = @Content(examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
					value = "{\"userId\":\"3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b\",\"amountUsd\":100.00}")))
	public CompletableFuture<RemittanceResponse> reverse(@Valid @RequestBody ReverseRemittanceRequest request) {
		return remittanceService.reverse(request);
	}

}
