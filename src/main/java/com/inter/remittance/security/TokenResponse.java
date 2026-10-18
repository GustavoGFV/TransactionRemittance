package com.inter.remittance.security;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bearer token to send in the Authorization header")
public record TokenResponse(
		@Schema(description = "Signed JWT", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
		@Schema(description = "Token type", example = "Bearer") String tokenType,
		@Schema(description = "Seconds until the token expires", example = "3600") long expiresIn) {
}
