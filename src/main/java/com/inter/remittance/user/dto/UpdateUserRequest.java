package com.inter.remittance.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Email @Size(max = 254) String email) {
}
