package com.inter.remittance.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.inter.remittance.user.model.DocumentType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A registered user")
public record UserResponse(
		@Schema(description = "Public user id", example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b") UUID id,
		@Schema(description = "Full name or company name", example = "Maria Silva") String name,
		@Schema(description = "Email address (stored in lower case)", example = "maria.silva@mail.com") String email,
		@Schema(description = "CPF or CNPJ without punctuation", example = "52998224725") String document,
		@Schema(description = "Kind of document", example = "CPF") DocumentType documentType,
		@Schema(description = "BRL balance", example = "1500.00") BigDecimal balanceBrl,
		@Schema(description = "USD balance", example = "250.50") BigDecimal balanceUsd,
		@Schema(description = "Creation timestamp (UTC)", example = "2026-10-18T03:10:00Z") Instant createdAt,
		@Schema(description = "Last update timestamp (UTC)", example = "2026-10-18T03:15:00Z") Instant updatedAt) {
}
