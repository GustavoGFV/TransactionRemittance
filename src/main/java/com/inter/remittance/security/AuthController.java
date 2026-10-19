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
@Tag(name = "Authentication", description = "Issues the Bearer (JWT) tokens required by the secured endpoints. "
		+ "ADMIN tokens (API client) manage users; USER tokens (e-mail and password) operate on the user's own remittances")
public class AuthController {

	private final TokenService tokenService;

	public AuthController(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	/**
	 * Issues an ADMIN token in exchange for the API client credentials.
	 * @param request the token request containing client credentials
	 * @return the token response containing the signed JWT
	 */
	@PostMapping("/token")
	@Operation(summary = "Issue an ADMIN access token",
			description = "Exchanges the API client credentials for a JWT with the ADMIN role, required to create and list users. "
					+ "Send it as 'Authorization: Bearer <token>'. It cannot operate remittances.")
	@ApiResponse(responseCode = "200", description = "Token issued")
	@ApiResponse(responseCode = "400", description = "Missing credentials",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Invalid credentials",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public TokenResponse token(@Valid @RequestBody TokenRequest request) {
		return tokenService.issue(request);
	}

	/**
	 * Issues a USER token in exchange for a registered user's e-mail and password.
	 * @param request the login request
	 * @return the token response containing the signed JWT, whose subject is the user's public id
	 */
	@PostMapping("/login")
	@Operation(summary = "Issue a USER access token",
			description = "Authenticates a registered user by e-mail and password. **The user must be created first with `POST /users`.** The token is bound to that user: "
					+ "balance, preview, buy and reverse only work for the user id it was issued to, and the user can read, "
					+ "update and delete only their own profile.")
	@ApiResponse(responseCode = "200", description = "Token issued")
	@ApiResponse(responseCode = "400", description = "Missing e-mail or password",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Invalid e-mail or password",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return tokenService.login(request);
	}

}
