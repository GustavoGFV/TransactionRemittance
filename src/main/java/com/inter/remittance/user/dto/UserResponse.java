package com.inter.remittance.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.inter.remittance.user.model.DocumentType;

public record UserResponse(
		UUID id,
		String name,
		String email,
		String document,
		DocumentType documentType,
		BigDecimal balanceBrl,
		BigDecimal balanceUsd,
		Instant createdAt,
		Instant updatedAt) {
}
