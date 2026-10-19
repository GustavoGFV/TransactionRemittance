package com.inter.remittance.user.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.inter.remittance.user.dto.CreateUserRequest;
import com.inter.remittance.user.dto.PageResponse;
import com.inter.remittance.user.dto.UpdateUserRequest;
import com.inter.remittance.user.dto.UserFilter;
import com.inter.remittance.user.dto.UserResponse;
import com.inter.remittance.user.dto.UserSortField;
import com.inter.remittance.user.mapper.UserMapper;
import com.inter.remittance.user.model.DocumentType;
import com.inter.remittance.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User", description = "User management (CPF/CNPJ) with BRL and USD balances")
public class UserController {

	private static final String PROBLEM = "application/problem+json";

	private final UserService userService;
	private final UserMapper mapper;

	public UserController(UserService userService, UserMapper mapper) {
		this.userService = userService;
		this.mapper = mapper;
	}

	/**
	 * Endpoint to create a new user.
	 * @param request the create user request containing user details
	 * @return the created user response with public UUID
	 */
	@SecurityRequirements // public sign-up: no token required
	@PostMapping
	@Operation(summary = "Create a user",
			description = "Public sign-up (no token). Registers a person (**CPF**) or company (**CNPJ**) in the `document` field.\n\n"
					+ "| Field | Required | Behavior |\n|---|---|---|\n"
					+ "| `name`, `email`, `document`, `password` | yes | Email and document must be valid and unique; password is stored as a BCrypt hash |\n"
					+ "| `balanceBrl`, `balanceUsd` | **OPTIONAL** | **Default to 0** |\n\n"
					+ "The response has **no body**: nothing about the user is echoed back. Next step: `POST /auth/login` with the same email and password.")
	@ApiResponse(responseCode = "201", description = "User created. Empty body (no user data is echoed back); the Location header carries only the new public id", content = @Content)
	@ApiResponse(responseCode = "400", description = "Invalid payload (e.g. invalid CPF/CNPJ or email, password shorter than 8 characters)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Email or document already registered",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "Rate limit exceeded; see the Retry-After header",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<Void> create(@Valid @RequestBody CreateUserRequest request) {
		UserResponse created = mapper.toResponse(userService.create(request));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).build();
	}

	/**
	 * Endpoint to get a user by its public UUID.
	 * @param id the public user id (UUID)
	 * @return the user response with public UUID
	 */
	@ApiResponse(responseCode = "403", description = "Requires an ADMIN token or the USER token of this same user", content = @Content)
	@PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
	@GetMapping("/{id}")
	@Operation(summary = "Get a user by id", description = "Returns the user identified by its public UUID")
	@ApiResponse(responseCode = "200", description = "User found")
	@ApiResponse(responseCode = "404", description = "User not found",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public UserResponse findById(
			@Parameter(description = "Public user id (UUID)", required = true,
					example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b")
			@PathVariable UUID id) {
		return mapper.toResponse(userService.findById(id));
	}

	/**
	 * Endpoint to list users with optional filters and pagination.
	 * @param name filter by part of the name (optional)
	 * @param email filter by part of the email (optional)
	 * @param documentType filter by document type (optional)
	 * @param page zero-based page index (optional, default 0)
	 * @param size page size (optional, default 20)
	 * @param sortBy property used to sort the results (optional, default "createdAt")
	 * @param direction sort direction (optional, default "ASC")
	 * @return a paginated list of users matching the filters
	 */
	@ApiResponse(responseCode = "403", description = "Requires an ADMIN token (POST /auth/token)", content = @Content)
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping
	@Operation(summary = "List users",
			description = "Paginated list (ADMIN). **All filters are OPTIONAL** and combined with AND; "
					+ "text filters are case-insensitive and match any part of the value.")
	@ApiResponse(responseCode = "200", description = "Page of users (empty when nothing matches)")
	@ApiResponse(responseCode = "400", description = "Invalid query parameter (e.g. size above 100)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public PageResponse<UserResponse> findAll(
			@Parameter(description = "Filter by part of the name", example = "maria")
			@RequestParam(required = false) @Size(max = 150) String name,
			@Parameter(description = "Filter by part of the email", example = "@mail.com")
			@RequestParam(required = false) @Size(max = 254) String email,
			@Parameter(description = "Filter by document type", example = "CPF")
			@RequestParam(required = false) DocumentType documentType,
			@Parameter(description = "Zero-based page index", example = "0")
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size (1 to 100)", example = "20")
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@Parameter(description = "Property used to sort the results", example = "createdAt")
			@RequestParam(defaultValue = "createdAt") UserSortField sortBy,
			@Parameter(description = "Sort direction", example = "DESC")
			@RequestParam(defaultValue = "ASC") Sort.Direction direction) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy.property()));
		return PageResponse.from(userService.findAll(new UserFilter(name, email, documentType), pageable), mapper::toResponse);
	}

	/**
	 * Endpoint to update a user's name and email.
	 * @param id the public user id (UUID)
	 * @param request the update user request containing new name and email
	 * @return the updated user response with public UUID
	 */
	@ApiResponse(responseCode = "403", description = "Requires an ADMIN token or the USER token of this same user", content = @Content)
	@PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
	@PutMapping("/{id}")
	@Operation(summary = "Update name and email",
			description = "The document is immutable and balances change only through transactions")
	@ApiResponse(responseCode = "200", description = "User updated")
	@ApiResponse(responseCode = "400", description = "Invalid payload",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "User not found",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Email already used by another user",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public UserResponse update(
			@Parameter(description = "Public user id (UUID)", required = true,
					example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b")
			@PathVariable UUID id,
			@Valid @RequestBody UpdateUserRequest request) {
		return mapper.toResponse(userService.update(id, request));
	}

	/**
	 * Endpoint to delete a user by its public UUID.
	 * @param id the public user id (UUID)
	 * @return a response entity with no content if the deletion is successful
	 */
	@ApiResponse(responseCode = "403", description = "Requires an ADMIN token or the USER token of this same user", content = @Content)
	@PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a user", description = "Permanently removes the user")
	@ApiResponse(responseCode = "204", description = "User deleted")
	@ApiResponse(responseCode = "404", description = "User not found",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<Void> delete(
			@Parameter(description = "Public user id (UUID)", required = true,
					example = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b")
			@PathVariable UUID id) {
		userService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
