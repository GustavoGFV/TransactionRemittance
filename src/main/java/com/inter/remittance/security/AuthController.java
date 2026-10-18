package com.inter.remittance.security;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Issues the Bearer (JWT) token required by the secured endpoints")
public class AuthController {

	private final TokenService tokenService;

	public AuthController(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	/**
	 * Issues a JWT in exchange for the API client credentials.
	 * @param request the token request containing client credentials
	 * @return the token response containing the signed JWT
	 */
	@PostMapping("/token")
	@Operation(summary = "Issue an access token",
			description = "Exchanges the API client credentials for a signed JWT. Send it as 'Authorization: Bearer <token>'.")
	@ApiResponse(responseCode = "200", description = "Token issued")
	@ApiResponse(responseCode = "400", description = "Missing credentials",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Invalid credentials",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public TokenResponse token(@Valid @RequestBody TokenRequest request) {
		return tokenService.issue(request);
	}

}
