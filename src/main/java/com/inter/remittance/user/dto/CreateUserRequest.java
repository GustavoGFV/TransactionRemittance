package com.inter.remittance.user.dto;

import java.math.BigDecimal;

import com.inter.remittance.user.validation.ValidDocument;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @ValidDocument String document,
		@PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal balanceBrl,
		@PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal balanceUsd) {
}
